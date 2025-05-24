Getting Started
===============

Dis4IRC requires **Java 11** or newer to run, so make sure you have that
installed before proceeding.

Startup
-------

To get started with Dis4IRC, start by downloading or building a jar. Next, run
Dis4IRC from the commandline. Dis4IRC will generate a config and then complain
that it's missing some important information and exit.

It should look like this:
```bash                                                               
$ java -jar Dis4IRC-1.6.5.jar
[18:23:19] [init] [INFO] - Dis4IRC v1.6.5-96b6acc
[18:23:19] [init] [INFO] - Source available at https://github.com/zachbr/Dis4IRC
[18:23:19] [init] [INFO] - Available under the MIT License
[18:23:19] [init] [INFO] - Loading config from: config.hocon
[18:23:19] [init] [INFO] - Log level set to INFO
[18:23:19] [init] [INFO] - Starting bridge: default
[18:23:19] [init] [ERROR] - Discord API key left empty for bridge: default
Exception in thread "main" java.lang.IllegalArgumentException: Cannot start default bridge with above configuration errors!
	at io.zachbr.dis4irc.config.ConfigurationUtilsKt.toBridgeConfiguration(ConfigurationUtils.kt:238)
	at io.zachbr.dis4irc.Dis4IRC.startBridge(Dis4IRC.kt:132)
	at io.zachbr.dis4irc.Dis4IRC.<init>(Dis4IRC.kt:103)
	at io.zachbr.dis4irc.Dis4IRCKt.main(Dis4IRC.kt:35)
```

As you can see, there was an error reading the Discord API key from the config file,
that's fine as we haven't added one yet. Let's do that.  You'll find the config file
in your current working directory with the name `config.hocon`

Let's open it up and configure Dis4IRC.

Discord API
-----------

First, you need to setup a Discord API application and get a bot token, for more information
on how to do that, see the other docs page on that: [Registering a Discord API Application](https://github.com/zachbr/Dis4IRC/blob/master/docs/Registering-A-Discord-Application.md)

Once you have your Discord API application setup, copy its bot token into your config
file. It goes with the `discord-api-key` field. It should look like this when you're
done.

```hocon
# Your discord API key you registered your bot with
discord-api-key="your-key-exactly-here"
```

IRC Server
----------

Now that we've finished with that, we need to tell Dis4IRC which IRC server you want to
bridge to. The exact values here will depend on your IRC server but should be similar to
what you would put into a normal IRC client.

For the purposes of this introduction, we only care about getting you connected to a server,
so we'll skip some of the customization options and just focus on the server setup.

Start by changing the server field to your IRC server's hostname or IP address.
Next, configure the port you want the bridge to connect on, `6667` (no-ssl) and `6697` (ssl)
are the typical values for most IRC servers

If your IRC server does not support SSL or you're connecting on an unencrypted port, you
should change `use-ssl` to `false` to tell te bot not to use SSL when connecting.

Finally, set the `nickname` field to whatever you want the bot to appear as when speaking.

Channel Mappings
----------------

Now that we have connections to both Discord and IRC, we need to tell the bot what channels it should
be bridging. It is strongly recommended you use Discord channel *IDs* rather than channel names for this.

To get a Discord channel's ID, right click on the channel name in your client and click `Copy ID` at the
bottom of the context menu. If you don't see it there, go into your Discord client settings, in the "Appearance"
settings, and scroll to the bottom under "Advanced", enable "Developer Mode". You should now be able to copy
channel IDs.

Next, paste that Discord channel ID on the right side of the `channel-mappings` sub-entry. It will be used as a
key, and the IRC channel name will be used as a value. It is important that when you add the IRC channel name
that you **include** the `#` preceding the channel name.

It should look something like this:
```hocon
# Mappings are the channel <-> channel bridging configurations
channel-mappings {
    "421260739970334720"="#channel-to-bridge"
}
```

Configuring WebHooks
---------------------

You can optionally enable channel-based webhooks for the Discord side of the bridge.
This will allow the bot to blend more seamlessly into your channels and allow avatars to show up for users with
matching names on both sides of the bridge.

We need to start out by setting up a new webhook in Discord for each channel you want to use with webhooks.
To do so, hover your mouse on the channel name and click the little gear icon that appears next to it, as if you
were going to edit the channel. Click the "Webhooks" tab in the channel settings, then click the purple "Create Webhook"
button. Name it however you can best remember what its for, make sure the channel matches the channel you want to
bridge, then copy the webhook URL at the bottom.

Now we need to add that webhook URL to the config. Find the `discord-webhooks` section, and paste the URL into the
right side of the field. Now, paste your discord channel ID from the `channel-mappings` section into the left side.

**It is important that the channel ID in the `discord-webhooks` section matches the channel ID in the `channel-mappings`
section exactly.** If it does not, it will not register properly. The log will also warn you if there's any issues using
your webhook.

If you **are** using webhooks, your config should look like this:
```hocon
# Match a channel id to a webhook URL to enable webhooks for that channel
discord-webhooks {
    "421260739970334720"="https://discordapp.com/api/webhooks/421260739970334720/the-rest-of-your-url"
}
```

**If you don't want to use any webhooks** you must delete the default entry in that section or the config validation
will complain.

If you're **not** using webhooks, your config should look like this:
```hocon
# Match a channel id to a webhook URL to enable webhooks for that channel
discord-webhooks {
}
```

Starting It Up
--------------

At this point, you should be ready to start the bridge again. Watch the console output closely, if there's any
mistakes in the config it should alert you to them before exiting again. If you get one, don't worry. Just read
the info and correct your config, then start it up again.

The bridge should have connected to the IRC server and joined the mapped channels you specified but it won't be
in your Discord server yet, we'll have to tell it to join. That's easy though.

In your console log you should see a line with an invite link, copy and paste that link into a browser and then
give the bot permission to join your Guild. It should look something like this:∂

```
[19:48:51] [default] [INFO] - Discord Bot Invite URL: https://discordapp.com/oauth2/authorize?scope=bot&client_id=yourbotidhere
```

And then you should be all good to go! If you aren't using webhooks you will need to make sure the bot has the
permissions and/or roles required to speak in your channels and guild. If you are using webhooks, you should be
all set.

[Docs Index](https://github.com/zachbr/Dis4IRC/tree/master/docs)
