## Voice Tracker

Service that monitors Discord activity, records voice of selected users, sends recordings to Telegram, and optionally transcribes them via OpenAI Whisper. It also relays Discord presence/voice events to a main Telegram chat and provides admin commands to control playback in a Discord voice channel.

### Features
- **Discord tracking**: connects to a guild voice channel and tracks members.
- **Selective recording**: records only users listed in `usernames-to-record`, buffers chunks, and emits consolidated MP3 files.
- **Telegram delivery**: sends recordings to a dedicated Telegram voices chat (archive).
- **Transcription (optional)**: if OpenAI key is provided, audios forwarded into a specific topic (subgroup) of the same chat are merged and transcribed with Whisper (`whisper-1`), text is attached as a caption.
- **Status relays**: Discord events (presence/voice) are posted to the main Telegram chat.
- **Telegram admin commands**:
  - `/play <mp3_url>`: play MP3 into the Discord voice channel.
  - `/send` (reply to an audio): send the attached audio to Discord.
  - `/stop`: stop playback in Discord.
  - `/get_id` (reply to an audio): get Telegram `file_id` of the file.
  - `/h`: list available mention tags.

### How it works (short)
- Micronaut/Kotlin backend starts a Telegram bot (long polling by default) and a Discord bot (JDA 5).  
- Audio from Discord is collected, encoded to MP3, and sent to the configured Telegram chat.  
- Transcription uses OpenAI API when `open-ai.key` is present.

## Configuration
Main settings live in `src/main/resources/application.yml`.

```yaml
telegram:
  target-chat-id: # main Telegram chat for Discord status messages
  subgroup-id:    # topic id in the same chat used for "favorite audios" and transcription (optional)
  voices-chat-id: # Telegram chat where all recorded audios will be stored
  tags-map:       # map of tags for quick mentions: /all, /tag1, ...
  bot:
    webhook-url:  # Telegram bot webhook URL (optional; long polling by default)
    token:        # Telegram bot token

discord:
  guild-name:          # Discord guild name
  usernames-to-record: # Discord usernames to be recorded
    - "discord_username1"
    - "discord_username2"
  bot:
    token: # Discord bot token
    name:  # Discord bot name

open-ai:
  key: # OpenAI key to enable transcription/voice features (optional)

admins:
  main-admin:   # main admin Telegram ID (receives logs/errors)
  telegram-ids: [] # Telegram IDs allowed to use admin commands
```

Recommended: keep separate environment configs and never commit secrets:
- `src/main/resources/application-local.yml` (gitignored)
- `src/main/resources/application-prod.yml` (gitignored)

To select a Micronaut profile, set `MICRONAUT_ENVIRONMENTS=local` or `prod`.

## Requirements
- JDK 21+
- Docker (optional for containerization)

## Run locally
```bash
./gradlew run
```

By default the Telegram bot uses long polling. Optional webhook endpoint: `POST /bot/voice-tracker-bot`.

## Build and run with Docker
Build native AOT Docker image:
```bash
./gradlew clean optimizedDockerBuild
```

Run the container (mount your config):
```bash
docker run --rm \
  -e MICRONAUT_ENVIRONMENTS=prod \
  -e MICRONAUT_CONFIG_FILES=/app/application-prod.yml \
  -v $(pwd)/src/main/resources/application-prod.yml:/app/application-prod.yml:ro \
  -p 8080:8080 \
  voice-tracker
```

Optionally mount directories for audio (`/audio`) or caches (`mp3/`, `tmp/`).

## Tech
- Micronaut, Kotlin
- Discord: JDA 5, LavaPlayer
- Telegram: `kotlin-telegram-bot`
- OpenAI API (Whisper for transcription)

## Important (privacy)
Recording voice channels may violate server rules or local laws. Use only where you have explicit right and participant consent.