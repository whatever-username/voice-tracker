const {GatewayIntentBits, Client} = require('discord.js');
const axios = require('axios');

const {Telegraf} = require('telegraf')

const client = new Client({
  intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildWebhooks, GatewayIntentBits.GuildVoiceStates, GatewayIntentBits.GuildPresences,]
});

const discordBotToken = 'MTEzODg0ODMwMTQ5NjY4NDYxNA.GXNEmu.U5PcmaLQG_tcg8JBebPFMCxQuBsAUI0cnbT4fw';
const telegramBotToken = '6612952814:AAHOHGYOTamPKcvl5olpb0TPadAu_h_iJtU';
const targetChatId = '-1001606190308'; // Replace with the chat_id you want to send the message to
const telegramApiUrl = `https://api.telegram.org/bot${telegramBotToken}`;

client.once('ready', () => {
  console.log(`Logged in as ${client.user.tag}`);
});
var oldMessage = null
client.on('voiceStateUpdate', async (oldState, newState) => {
  var channel;
  if (oldState.channel == null) {
    channel = newState.channel
  } else {
    channel = oldState.channel
  }
  updateInfoByChannel(channel)
});

client.on('presenceUpdate', async (oldPresence, newPresence) => {
  const member = newPresence.member;
  let channel = member.guild.channels._cache.filter(it => it.name == "Лобби").values().next().value
  await updateInfoByChannel(channel)

});

client.login(discordBotToken);

const bot = new Telegraf(telegramBotToken);


bot.command('all', async (ctx) => {
  // List of user IDs to tag
  const userIDs = ['frozenheime', 'ioncihi', 'viktor_pshenichny', 'buslique', 'amsethis', 'trickygypsy', 'HikoWD', 'HedhoK', 'justluik', 'Quinsberry', 'electrokote'].map(it => "@" + it);
  const taggedMessage = `${userIDs.join(', ')}`;
  ctx.reply(taggedMessage);
});
bot.command('tbilisi', async (ctx) => {
  // List of user IDs to tag
  const userIDs = ['frozenheime', 'viktor_pshenichny', 'amsethis', 'trickygypsy', 'Quinsberry'].map(it => "@" + it);
  const taggedMessage = `${userIDs.join(', ')}`;
  // Send the tagged message
  ctx.reply(taggedMessage);
});
bot.command('prodyryavlennye_yebishnie_petykhi', async (ctx) => {
  // List of user IDs to tag
  const userIDs = ['frozenheime', 'buslique'].map(it => "@" + it);
  const taggedMessage = `${userIDs.join(', ')}`;
  // Send the tagged message
  ctx.reply(taggedMessage);
});
bot.command('h', async (ctx) => {
  // List of user IDs to tag
  const userIDs = ['/tbilisi', '/all']
  const taggedMessage = `${userIDs.join('\n')}`;
  // Send the tagged message
  ctx.reply(taggedMessage);
})
bot.launch();


axios.post(`${telegramApiUrl}/sendMessage`, {
  chat_id: targetChatId, text: `restarted`, parse_mode: "HTML"
})


async function updateInfoByChannel(channel) {
  var usersInVoice = channel.members.map(it => {
    const username = it.user.username;
    const isMuted = it.voice.mute;
    const isDeafened = it.voice.deaf;
    const status = '' + (username === "frozenheime" ? " 🐓" : "") + (isMuted ? "🙊" : "") + (isDeafened ? "🙉" : "")
    return `<b>${username}</b>${status}`;
  });
  var userIdToUsernameMap = getIdToUsernameMap(channel)

  var infoByActivities = getActivitiesText(channel)
  if (oldMessage || usersInVoice.length === 0 || infoByActivities) {
    try {
      const response = await axios.post(`${telegramApiUrl}/deleteMessage`, {
        chat_id: targetChatId, message_id: oldMessage
      });
    } catch (error) {
    }
    if (usersInVoice.length === 0 || !infoByActivities) {
      oldMessage = null
      return
    }
  }
  var text = `В войсе:\n${usersInVoice.join("\n")}` + "\n\n" + `<code>${infoByActivities}</code>`
  var response = await axios.post(`${telegramApiUrl}/sendMessage`, {
    chat_id: targetChatId, text: text, parse_mode: "HTML"
  })
    .then(response => {
      oldMessage = response.data.result.message_id;
    }).catch(error => {
      axios.post(telegramApiUrl, {
        chat_id: 668539715, text: error
      });
    });

}

function getIdToUsernameMap(channel) {
  var res = {}
  channel.guild.members._cache.forEach(it => {
    res[it.user.id] = it.user.username
  })
  return res
}

function getActivitiesText(channel) {
  return channel.guild.presences._cache.map(it => {
    var activities = null
    if (it.activities && it.activities.length != 0) {
      let a = it.activities[0]
      activities = a.name
      if (a.state) {
        activities += `. ${a.state}`
      }
      if (a.details) {
        activities += `. ${a.details}`
      }
    }
    return {
      username: getIdToUsernameMap(channel)[it.userId], text: activities
    }
  }).filter(it => it.text)
    .map(it => it.username + ': ' + it.text).join("\n")
}
