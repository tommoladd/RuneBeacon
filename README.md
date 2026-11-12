# RuneBeacon

A RuneLite plugin for friend groups. Create permanent groups for your friends or temporary ones
for a raid, invite people by name (or right-click them in your friends list or chat) or with an
invite code, and see each member's:

- Online status (logged in / AFK / logged out / last seen) and current world
- Location, area name and current activity (boss, raid, minigame, skill being trained)
- Custom status message
- HP, prayer, special attack, run energy, weight, spellbook, poison/venom
- Skills, inventory, equipment (including Dizana's quiver), active prayers and rune pouch

Members appear as pins on the world map with their name and world. Hold the ping hotkey (Shift by
default) and click a tile to highlight it for your group, or ping an item in a member's inventory
from the panel to highlight it in their game inventory.

Group owners can give individual members permissions to invite, kick, rename, extend temporary
groups and manage other members' permissions.

## Privacy

RuneBeacon needs the RuneBeacon server at `beacon.tommoladd.dev`, so it is **off until you turn on "Connect to RuneBeacon server"** in the plugin settings. Nothing is sent before then.

**Who can see your data:** only members of groups you have joined. You join a group only by accepting an invite or entering an invite code yourself, and you can leave at any time.

**What is sent:** your own status, and only the categories you leave switched on under the *Sharing* settings (location, activity, vitals, skills, inventory, equipment, prayers). Turning a
category off also removes it from the server. RuneBeacon never sends information about other players, apart from the name you type (or right-click) when you invite someone.

**Appearing offline:** RuneBeacon follows your in-game **private chat** setting. While it is set to
**Off** you appear offline to your groups, and your location, stats and map pin are removed, just as
you appear offline to your friends list. **On** or **Friends** shows you online. You can turn this
off with *Match private chat setting*.

**PvP safe mode (always on):** in the Wilderness, in PvP areas and on PvP-type worlds (PvP, High Risk,
Deadman, Bounty Hunter, PvP Arena, Last Man Standing), RuneBeacon shares only that you're online,
your world and "In the Wilderness" / "On a PvP world". Your location, map pin, HP, prayer, stats,
gear, inventory and prayers are removed until you leave, and tile and item pings are turned off.
RuneBeacon can't be used to scout or coordinate in PvP.

**What the server stores:**

| Data | Why | How long |
|---|---|---|
| Your RuneLite account hash (an anonymous ID RuneLite generates) and current RSN | To recognise you and show your name to your groups | Until you ask for it to be deleted |
| A login token (stored only as a hash) | To keep you signed in; the token itself is kept in your RuneLite settings | Until you ask for it to be deleted |
| Your groups, permissions and pending invites | To run groups | Until you leave or the group is deleted; temporary groups and invites expire automatically (invites after 7 days) |
| Your last shared status (the categories above) | So group members can see where you were when you went offline | Replaced every time you play; removed when you switch a category off |

**IP address:** connecting to any server reveals your IP address to it (that's what RuneLite's
warning on the setting refers to). RuneBeacon does not store it in its database; it is used for
rate limiting and can appear in short-lived server logs.

To have your data deleted, contact the plugin author through this plugin's GitHub repository.

## Credits

Area and activity region data (`regions.json`) is derived from RuneLite's Discord plugin,
Copyright (c) 2018, Tomas Slusny, BSD 2-Clause license.
