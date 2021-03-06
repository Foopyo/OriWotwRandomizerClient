package wotw.web.ui.bingo

import io.ktor.client.request.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.*
import kotlinx.css.properties.border
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.p
import styled.css
import styled.styledDiv
import styled.styledP
import wotw.io.messages.protobuf.*
import wotw.io.messages.protobuf.Position
import wotw.web.io.WebSocketComponent
import wotw.web.main.Application
import wotw.web.util.BACKEND_HOST
import wotw.web.util.BACKEND_PORT
import wotw.web.util.hbox
import wotw.web.util.vbox

external interface GameIdProps : RProps {
    var gameId: Long
}

external interface PlayerIdProps : RProps {
    var playerId: Long?
}

external interface BingoCardProps : GameIdProps {
    var useLatest: Boolean?
    var playerId: Long?
}

external interface BingoCardState : RState {
    var board: BingoBoard
}

external interface BingoSquareProps : RProps {
    var gridPosition: Position
    var size: Pair<LinearDimension?, LinearDimension?>?
    var boardSize: Int
    var text: String
    var completed: Boolean
    var goals: List<BingoGoal>
    var xEdge: Boolean
    var yEdge: Boolean
}

external interface BingoSquareState : RState {
    var marked: Boolean
}

external interface BingoGoalProps : RProps {
    var text: String
    var completed: Boolean
}

class BingoView : RComponent<GameIdProps, RState>() {
    override fun RBuilder.render() {
        vbox {
            hbox {
                css {
                    gap = Gap("10px")
                }
                child(BingoCardComponent::class) {
                    attrs.gameId = props.gameId
                }
                child(BingoPlayersComponent::class) {
                    attrs.gameId = props.gameId
                }
            }
        }
    }
}


val labelSize = 20.px
val gapSize = 5.px

class BingoCardComponent(props: BingoCardProps) : RComponent<BingoCardProps, BingoCardState>(props) {
    override fun BingoCardState.init(props: BingoCardProps) {
        board = BingoBoard(size = 5)
    }

    override fun componentDidMount() {
        GlobalScope.launch {
            val path = when {
                props.useLatest != true -> "bingo/${props.gameId}"
                props.playerId == null -> "bingo/latest"
                else -> "bingo/latest/${props.playerId}"
            }
            val boardData = Application.api.get<BingoData>(path = path)
            setState {
                this.board = boardData.board
            }
            Application.eventBus.send(SyncBingoPlayersMessage(boardData.players))
        }


        Application.eventBus.register(this, SyncBoardMessage::class) {
            setState {
                if (it.replace)
                    board = it.board
                else board += it.board
            }
        }
    }

    override fun componentWillUnmount() {
        Application.eventBus.unregisterAll(this)
    }

    override fun RBuilder.render() {
        styledDiv {
            css {
                border(width = 2.px, style = BorderStyle.solid, color = Color.aqua)
                marginRight = 3.em
                position = kotlinx.css.Position.relative
                width = LinearDimension("min(80%, 95vh)")
                before {
                    content = QuotedString("")
                    float = Float.left
                    paddingTop = 100.pct
                }
            }
            vbox {
                css {
                    gap = Gap(gapSize.toString())
                    float = Float.left
                    width = 100.pct
                    height = 100.pct
                }
                val size = state.board.size
                val cardRange = 1..size
                for (y: Int in 0..size + 1) {
                    hbox {
                        css {
                            gap = Gap(gapSize.toString())
                            width = 100.pct
                            height =
                                if (y !in cardRange) labelSize else LinearDimension("calc((100% - 2 * $labelSize - ${size + 1} * $gapSize) / $size)")
                            textAlign = TextAlign.center
                        }
                        for (x: Int in 0..size + 1) {
                            //why are grids not 0-based again? :<
                            val gridPos = Position(x + 1, y + 1)
                            val width = if (x !in cardRange) labelSize else null
                            val height = if (y !in cardRange) labelSize else null
                            val square = state.board[Position(x, y)]
                            val text = when {
                                square != null -> square.text
                                x == 0 && y == 0 || x > size && y > size -> "X"
                                x == 0 && y > size || x > size && y == 0 -> "Y"
                                x !in cardRange -> y.toString()
                                y !in cardRange -> (x + 64).toChar().toString()
                                else -> ""
                            }
                            val completed = when {
                                square != null -> square.completed
                                x == 0 && y == 0 || x > size && y > size -> cardRange.all {
                                    state.board[Position(it, it)]?.completed == true
                                }
                                x == 0 && y > size || x > size && y == 0 -> cardRange.all {
                                    state.board[Position(size + 1 - it, it)]?.completed == true
                                }
                                x !in cardRange -> cardRange.all {
                                    state.board[Position(it, y)]?.completed == true
                                }
                                else -> cardRange.all {
                                    state.board[Position(x, it)]?.completed == true
                                }
                            }

                            child(BingoSquareComponent::class) {
                                attrs.xEdge = x !in cardRange
                                attrs.yEdge = y !in cardRange
                                attrs.gridPosition = gridPos
                                attrs.size = width to height
                                attrs.boardSize = size
                                attrs.completed = completed
                                attrs.goals = square?.goals ?: emptyList()
                                attrs.text = text
                            }
                        }
                    }
                }
            }
            child(WebSocketComponent::class) {
                if (props.useLatest == true) {
                    attrs.url = "wss://$BACKEND_HOST:$BACKEND_PORT/api/bingosync/latest"
                    if (props.playerId != null)
                        attrs.url += "/${props.playerId}"
                } else {
                    attrs.url = "wss://$BACKEND_HOST:$BACKEND_PORT/api/bingosync/${props.gameId}"
                }
            }
        }
    }
}

class BingoSquareComponent : RComponent<BingoSquareProps, BingoSquareState>() {
    override fun BingoSquareState.init() {
        marked = false
    }

    override fun RBuilder.render() {
        styledDiv {
            css {
                width = props.size?.first
                    ?: LinearDimension("calc((100% - 2 * $labelSize  - ${props.boardSize + 1} * $gapSize) / ${props.boardSize})")
                height = 100.pct
                textAlign = TextAlign.center
                fontWeight = FontWeight.bold
                backgroundColor =
                    if (props.completed) Color.green else if (state.marked) Color.lightBlue else Color.lightGray
            }
            when {
                props.xEdge && !props.yEdge -> styledP {
                    css {
                        fontWeight = FontWeight.normal
                        marginTop = 0.em
                        paddingTop = 4.em // TODO: actually center
                    }
                    +props.text
                }
                props.xEdge || props.yEdge -> styledP {
                    css {
                        fontWeight = FontWeight.normal
                        marginTop = 0.em
                        paddingTop = 0.em
                    }
                    +props.text
                }
                else -> p {
                    +props.text
                }
            }

            attrs {
                onClickFunction = {
                    it.preventDefault()
                    setState {
                        marked = !marked
                    }
                }
            }
            for (it in props.goals) {
                child(BingoGoalComponent::class) {
                    attrs.text = it.text
                    attrs.completed = it.completed
                }
            }
        }
    }
}

class BingoGoalComponent(props: BingoGoalProps) : RComponent<BingoGoalProps, RState>(props) {
    override fun RBuilder.render() {
        styledP {
            css {
                fontWeight = FontWeight.normal
            }
            +props.text
            if (props.completed) {
                css {
                    backgroundColor = Color.greenYellow
                }
            }
        }
    }
}