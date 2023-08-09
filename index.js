const {GatewayIntentBits, Client} = require('discord.js');
const axios = require('axios');
const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildWebhooks,
    GatewayIntentBits.GuildVoiceStates,
  ]
});

const discordBotToken = 'MTEzODg0ODMwMTQ5NjY4NDYxNA.Glob6l.VgAmX_uke_FiHxQfreR9ht_rJulx76f-23lbws';
const telegramBotToken = '6612952814:AAHOHGYOTamPKcvl5olpb0TPadAu_h_iJtU';
const targetChatId = '-1001606190308'; // Replace with the chat_id you want to send the message to
const telegramApiUrl = `https://api.telegram.org/bot${telegramBotToken}`;

client.once('ready', () => {
  console.log(`Logged in as ${client.user.tag}`);
});
var oldMessage = null
client.on('voiceStateUpdate', async (oldState, newState) => {
  if ((!oldState.channel && newState.channel) || (oldState.channel && !newState.channel)) {
    var channel;
    if (oldState.channel == null) {
      channel = newState.channel
    } else {
      channel = oldState.channel
    }
    var users = channel.members.map(it => "<b>" + it.user.username + "</b>")


    if (oldMessage || users.length === 0) {
      try {
        const response = await axios.post(`${telegramApiUrl}/deleteMessage`, {
          chat_id: targetChatId,
          message_id: oldMessage
        });
      } catch (error) {
        await axios.post(telegramApiUrl, {
          chat_id: 668539715,
          text: error
        });
      }
      if (users.length === 0) {
        oldMessage = null
        return
      }
    }

    var response = await axios.post(`${telegramApiUrl}/sendMessage`, {
      chat_id: targetChatId,
      text: `В войсе:\n${users.join("\n")}`,
      parse_mode: "HTML"
    })
      .then(response => {
        oldMessage = response.data.result.message_id;
      }).catch(error => {
        axios.post(telegramApiUrl, {
          chat_id: 668539715,
          text: error
        });
      });


  }
});

client.login(discordBotToken);
