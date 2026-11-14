# CreativeBox

A Minecraft plugin that creates isolated sandbox worlds where players can build, experiment, and explore without affecting the main world.

## Features

- **Sandbox Worlds** — Create unlimited temporary or permanent sandbox instances
- **Player Snapshots** — Save and restore player state (inventory, position, health)
- **Portal System** — Easy access to sandboxes via portals
- **Zero Dependencies** — Pure Paper API plugin, no third-party libraries needed

## Installation

1. Download the latest release JAR from [Releases](https://github.com/niubikelashi/CreativeBox/releases)
2. Place it in your server's `plugins/` directory
3. Restart or reload your server

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sandbox create <name>` | Create a new sandbox world | `creativebox.create` |
| `/sandbox enter <name>` | Enter a sandbox world | `creativebox.use` |
| `/sandbox leave` | Leave current sandbox | `creativebox.use` |
| `/sandbox list` | List available sandboxes | `creativebox.list` |
| `/sandbox delete <name>` | Delete a sandbox world | `creativebox.delete` |

## Configuration

Edit `plugins/CreativeBox/config.yml`:

```yaml
world:
  seed: "default"
  difficulty: normal
  pvp: true
  
teleport:
  safe-delay: 3
  cooldown: 10
```

## Building from Source

Requires Java 17+ and Maven:

```bash
mvn clean package -DskipTests
```

The compiled JAR will be in `target/`.
