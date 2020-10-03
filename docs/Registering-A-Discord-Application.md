# Registering a Discord API Application

To use Dis4IRC, you will need a bot token from Discord API.

To get a bot token, you will need to register a new application.

Let's get started.

-------

## Creating an Application
Start by going to the [Discord Developer Portal](https://discord.com/developers/)
and logging in with your Discord account.

Next, on the main portal page, select the large "Create an application" placeholder.

![create-an-app](https://i.imgur.com/s4lyWlO.png)

Give your application a name that makes sense to you, I'll be using "Bridge".

If prompted, save your changes.

## Creating a Bot

Then click the "Bot" tab in the sidebar on the left.

![application-settings](https://i.imgur.com/l1aOYvV.png)

Select "Add bot" on the right side.

![add-a-bot](https://i.imgur.com/mE1Lt7K.png)

Give your bot a username, if you aren't using webhooks, you'll see this name a lot.

You can also give it an icon, if you aren't using webhooks you'll see this a lot too.

Now click the "Copy" button under the "Token" section. That is your bot's Discord API token that
you should paste into the config file.

![copy-token](https://i.imgur.com/vBsNirQ.png)

## Gateway Intents

You're almost done, just one more thing. Scroll down, under "Privileged Gateway Intents" and make sure that
the "Server Members Intent" is set to **On**.

Dis4IRC requires this intent to properly cache the member list for things like pings from IRC.

![gateway-intents](https://i.imgur.com/QIohhXv.png)

If you do not set this, you will receive an error message like this on start up:
```
CloseCode(4014 / Disallowed intents. Your bot might not be eligible to request a privileged intent such as GUILD_PRESENCES or GUILD_MEMBERS.)
```

Back to the [Getting Started page](https://github.com/zachbr/Dis4IRC/blob/master/docs/Getting-Started.md)
