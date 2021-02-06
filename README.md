##### Build status: ![yes](https://img.shields.io/badge/pp-UP-green)

# CompetitORE

ORE's competition management plugin.

## Full Command Usage

| Command | Description |
| --- | --- |
| `/comp cancel` | Cancel a confirmation item |
| `/comp confirm` | Confirm a confirmation item |
| `/comp enter` | Enter the competition |
| `/comp event` | [Manage the competition](#event-management) |
| `/comp home` | Teleports you to your competition plot |
| `/comp info` | Get the current event information, or time of next event (Default command)|
| `/comp leave` | Leave the competition |
| `/comp list (finished\|unfinished)?` | List competitor plots, finished, unfinished, or both |
| `/comp reload` | Reload plugin |
| `/comp team (add\|finish\|unfinish\|` | [Manage your team and submission](#team) |
| `/comp time` | View the time left in the competition |
| `/comp version` | Return plugin version |
| `/comp view [competitor\|?,?]` | Visit a competitors plot, or a plot with coordinates separated by `,` |

### Team

The `team` subcommand provides you three other subcommands: `add`, `finish`, and `unfinish`:

`add [player]` -  Add a player to your team. Once the player is added, the added player will now be able to manage your team and therefore your build. **Be wise in choosing who you plan on working with**.

`finish` - Submit your team's completed competition build. Labeling your build as completed does not prevent you from making further changes. Builds labeled as completed can be marked uncompleted.

`unfinish` - Unsubmits your team's submitted competition build. This can be undone by resubmitting.

<sub>Note: Even for solo competitions where players are not permitted to work together, players would still have to manage their build submission status with the `team` subcommand.</sub>

### Event Management

The `event` subcommand is a privileged subcommand that gives Staff and hosts the ability to manage a current or upcoming event.

| Subcommand | Description |
| --- | --- |
| `description` | Sets the description of this or the upcoming event |
| `judge (add\|remove\|clear)` | Add or remove a player or clear all judges |
| `start` | Start the event manually (Future improvement) |
| `stop` | Stop the event manually (Future improvement) |
| `teamsize [1-5]` | Set the team size |
| `winner` | Declare the event winner (Future improvement) |
