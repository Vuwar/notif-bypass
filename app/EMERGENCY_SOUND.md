# Optional emergency sound

The helper `MyNotificationListener.playEmergencySound()` resolves
`R.raw.emergency_tone` at runtime. To enable it:

1. Create the folder `app/src/main/res/raw/` if it doesn't exist.
2. Drop an audio file named exactly `emergency_tone.mp3` (or `.ogg` / `.wav`)
   into that `res/raw/` folder.
3. Uncomment the `// playEmergencySound()` line in `onNotificationPosted()`.

The call uses `resources.getIdentifier(...)` so the project still compiles even
when no such file exists yet — it simply logs a warning and skips playback.

> NOTE: Every file placed in `res/raw/` becomes a packaged Android resource, so
> its name must be lowercase and contain only letters, digits, and underscores
> (`a-z`, `0-9`, `_`). That's why this note lives here as Markdown rather than
> inside `res/raw/`.
