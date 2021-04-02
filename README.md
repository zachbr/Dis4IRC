Dis4IRC
=======
A modern Discord <-> IRC Bridge

Published under the [MIT License](https://github.com/zachbr/Dis4IRC/blob/master/LICENSE.md).

Features
--------
* Markdown and Modern IRC Client Features
* Paste support for long messages
* Channel Join/Quit broadcasts
* Discord webhook support
* Non-prefixed messages for other IRC bots to handle
* IRC anti-ping zero width character in usernames
* User, channel, role, and emote mentions

Getting Started
---------------
Please see the [Getting Started page](https://github.com/zachbr/Dis4IRC/blob/master/docs/Getting-Started.md).

Downloads
---------
* Release versions can be found on [GitHub releases](https://github.com/zachbr/Dis4IRC/releases).  
* The latest build from source can be found as a GitHub actions artifact.
  * Users authenticated with GitHub can access builds [here](https://github.com/zachbr/Dis4IRC/actions?query=event%3Apush+is%3Asuccess+branch%3Amaster).
  * Unauthenticated users can access the [latest version here](https://nightly.link/zachbr/Dis4IRC/workflows/gradle/master/dis4irc-jar).

Example Config
--------------
```hocon
# Dis4IRC Configuration File

# A list of bridges that Dis4IRC should start up
# Each bridge can bridge multiple channels between a single IRC and Discord Server
bridges {
    # A bridge is a single bridged connection operating in its own space away from all the other bridges
    # Most people will only need this one default bridge
    default {
        # Relay joins, quits, parts, and kicks
        announce-joins-and-quits=false
        # Relay extra verbose information you don't really need like topics and mode changes.
        announce-extras=false
        # Mappings are the channel <-> channel bridging configurations
        channel-mappings {
            "712345611123456811"="#bridgedChannel"
        }
        # Your discord API key you registered your bot with
        discord-api-key="NTjhWZj1MTq0L10gMDU0MSQ1.Zpj02g.4QiWlNw9W5xd150qXsC3e-oc156"
        # Match a channel id to a webhook URL to enable webhooks for that channel
        discord-webhooks {
            "712345611123456811"="https://discordapp.com/api/webhooks/712345611123456811/blahblahurl"
        }
        commands {
            pinned {
                enabled="true"
            }
            stats {
                enabled="true"
            }
        }
        # Configuration for connecting to the IRC server
        irc {
            anti-ping=true
            nickname=TestBridge2
            # Messages that match this regular expression will be passed to IRC without a user prefix
            no-prefix-regex="^\\.[A-Za-z0-9]"
            # Sets the max context length to use for messages that are Discord replies. 0 to disable.
            discord-reply-context-limit=90
            # A list of __raw__ irc messages to send
            init-commands-list=[
                "PRIVMSG NICKSERV info",
                "PRIVMSG NICKSERV help"
            ]
            port="6697"
            realname=BridgeBot
            server="irc.esper.net"
            use-ssl=true
            username=BridgeBot
        }
        mutators {
            paste-service {
                max-message-length=450
                max-new-lines=4
                # Number of days before paste expires. Use 0 to never expire.
                paste-expiration-in-days=7
            }
        }
    }
}
debug-logging=true

```

The Name
--------
The name is a typo of a typo of a bad idea of a misspoken phrase.
Let's just not go there :p

Built using
-----------
* [KittehIRCClientLib](https://github.com/KittehOrg/KittehIRCClientLib)
* [JDA (Java Discord API)](https://github.com/DV8FromTheWorld/JDA)
* [Configurate](https://github.com/SpongePowered/configurate)

