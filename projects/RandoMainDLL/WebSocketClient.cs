﻿using System;
using System.ComponentModel;
using System.Threading;
using Google.Protobuf;
using Network;
using WebSocketSharp;

namespace RandoMainDLL {
  public class WebSocketClient {
    public delegate void UberStateRegistrationHandler(Memory.UberId id);
    public delegate void UberStateUpdateHandler(Memory.UberId id, float value);

    public UberStateRegistrationHandler UberStateRegistered;
    public UberStateUpdateHandler UberStateChanged;
    private static string _domain;
    public static string Domain { 
      get {
        if (_domain == null) { 
          _domain = AHK.IniString("Paths", "URL");
          if(_domain == "")
            _domain = "wotw.orirando.com";
        }
        return _domain;
      }
    }
    public static bool WantConnection = true;
    public static string SessionId;

    public int ReconnectCooldown = 0;
    public int FramesSinceLastCheck = 0;
    private string ServerAddress => $"wss://{Domain}/api/game_sync/";

    private Websocket.Client.WebsocketClient socket;
    public bool IsConnected { get { return socket != null && socket.IsAlive; } }

    public void Connect() {
      new Thread(() => {
      //      PlayerId = player;
      if (socket != null) {
        Disconnect();
      }
      Randomizer.Log(ServerAddress, false);
      socket = new WebSocket(ServerAddress);
      socket.Log.Level = LogLevel.Info;
      socket.Log.Output = (logdata, output) => {
        Randomizer.Log($"Websocket says: {logdata.Message}", false, $"{logdata.Level}");
      };
      socket.OnError += (sender, e) => {
        Randomizer.Error("WebSocket", $"{e} {e?.Exception}", false);
        ReconnectCooldown = 5;
      };
      socket.OnClose += (sender, e) => {
        Randomizer.Log("Disconnected! Retrying in 5s");
        ReconnectCooldown = 5;
      };
      socket.OnMessage += HandleMessage;
      socket.OnOpen += new EventHandler(delegate (object sender, EventArgs args) {
        Randomizer.Log($"Socket opened", false);
        UberStateController.QueueSyncedStateUpdate();
        ReconnectCooldown = 0;
      });
      Randomizer.Log($"Attempting to connect to ${Domain}", false);

      socket.Connect();

    }

    public void Disconnect() {
      if (socket == null) {
        return;
      }

      socket.Close();
      socket = null;
    }

    public void SendUpdate(Memory.UberId id, float value) {
      if (socket == null) {
        return;
      }

      Packet packet = new Packet {
        Id = 3,
        Packet_ = new UberStateUpdateMessage {
          State = new UberId {
            // wolf started it :D
            Group = id.GroupID == 0 ? -1 : id.GroupID,
            State = id.ID == 0 ? -1 : id.ID
          },
          Value = value == 0f ? -1f : value
        }.ToByteString()
      };

      socket.Send(packet.ToByteArray());
    }

    public void HandleMessage(object sender, MessageEventArgs args) {
      try {
        var packet = Packet.Parser.ParseFrom(args.RawData);
        switch (packet.Id) {
          case 6:
            var printMsg = PrintTextMessage.Parser.ParseFrom(packet.Packet_);
            Randomizer.Log($"Server says {printMsg.Text} (f={printMsg.Frames} p={printMsg.Ypos})", false);
            AHK.Print(printMsg.Text, printMsg.Frames, printMsg.Ypos, true);
            break;
          case 5:
            var init = InitBingoMessage.Parser.ParseFrom(packet.Packet_);
            foreach (var state in init.UberId) {
              Randomizer.Log(state.ToString(), false);
              UberStateRegistered(new Memory.UberId(state.Group, state.State));
            }
            break;
          case 3:
            var update = UberStateUpdateMessage.Parser.ParseFrom(packet.Packet_);
            UberStateChanged(new Memory.UberId(update.State.Group, update.State.State), update.Value);
            break;
          default:
            break;
        }
      }
      catch (Exception t) {
        Randomizer.Error("t", t);
      }
    }
  }
}
