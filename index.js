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

client.once('ready', () => {
  console.log(`Logged in as ${client.user.tag}`);
});

client.on('voiceStateUpdate', async (oldState, newState) => {
  if (!oldState.channel && newState.channel) {
    // Member joined a voice channel
    const member = newState.member;
    const voiceChannelName = newState.channel.name;
    const messageText = `${member.displayName} joined voice channel ${voiceChannelName}`;

    // Send message to Telegram bot using HTTP request
    const telegramApiUrl = `https://api.telegram.org/bot${telegramBotToken}/sendMessage`;
    const payload = {
      chat_id: targetChatId,
      text: messageText
    };

    try {
      const response = await axios.post(telegramApiUrl, payload);
    } catch (error) {
      await axios.post(telegramApiUrl, {
        chat_id: 668539715,
        text: error
      });
    }
  }
});

client.login(discordBotToken);
