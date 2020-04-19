#include "pch.h"
#include "../../interceptionMacros.h"

bool is_collecting_experience = false;

INTERCEPT(6214992, void, SeinPickupProcessor__OnCollectExpOrbPickup, (SeinPickupProcessor_o* this_ptr, ExpOrbPickup_o* expOrbPickup), {
	if(expOrbPickup->MessageType){ //Any non-enemy exp drop has an associated messageBox
		is_collecting_experience = true;
	}
	SeinPickupProcessor__OnCollectExpOrbPickup(this_ptr, expOrbPickup);
	is_collecting_experience = false;
});

INTERCEPT(8379808, void, SeinLevel__set_Experience, (SeinLevel_o* this_ptr, int32_t value), {
	if(is_collecting_experience)
		return;

	SeinLevel__set_Experience(this_ptr, value);
});