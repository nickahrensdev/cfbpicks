# Groups Implementation

## Background 
- I am wanting admin users to be able to create groups. These will be isolated picking leagues.

## Requirements
- Groups should only be creatable by admin users (for now). at creation, Groups should require a name, a visibility (private or public), an optional password, an optional description. 
- Groups should also be configurable. I want each group to have dynamic settings such as when to cutoff picking. Pick cadence (weekly or daily). how many picks to make per cadence (allow no max by default). Different pick options (Winner, Spread, O/U). I want the group creator to be able to configure points per pick option for win/loss/push (can be positive or negative or zero and should be a decimal value), a setting for determining group length (options for never ending, per year, single season). There should also be a setting for if a user can make multiple picks for the same game (ex: pick spread and an O/U). I want there to also be a setting for a group type (elimination or pickem). all settings descibed above should apply for pickem. for elimination - I want the group creator to be able to set how many wrong picks before a user is eliminated. I also want the ability to configure how many times a team can be selected/picked to win (applys to groups that have winner and spread options. there should be a sub option for this setting if it should apply to winner/spread/both options). for elimination mode, allow settings for max picks per cadence (no limit as defult option). allow a minimum picks per cadence (allow zero to let users skip picking a day, default of 1). if default is not zero and a user doesnt make a pick within the cadence when games are pickable, they are eliminated.
- Leaderboards should be shown based on a selected group
- Games should be displayed based on a selected group
- I am wanting users to be able to view their group settings
- Group owners should have the ability to remove users from the group
- There should be a page for users to search for groups. Only public groups should be returned. Group search results should show the name, desciption, a lock or unlocked icon depending on if a password is required to join 
- Group owners should have the ability to delete their own groups. This should require a 2nd click to confirm deletion. user should be notified that all group data including picks will be lost
- group length: never ending keeps the group going indefinitly year over year. per year functionality should allow leaderboards to reset with each season (allow users to view leaderboards and picks from prior seasons), single season should end the group after the season ends.

- since each leaderboard/picks will now be group based, users will need to select a group before making a pick

- create an admin screen that allows app admins to manually manage groups and their members (add/remove)

- IMPORTANT: i dont want to lose current data in the database. If database changes need to be made to support groups, ask first. We will treat all current data in the database like it was created for 1 group for easy migration.

- to begin, lets focus on group creation with settings. this will allow me to create the first group which we can then assign current db data to.

- do not commit or push any code from this refactor
