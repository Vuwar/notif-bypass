PLACEHOLDER for the optional emergency sound.

The helper MyNotificationListener.playEmergencySound() references
R.raw.emergency_tone. To use it:

1. Drop an audio file named exactly  emergency_tone.mp3  (or .ogg / .wav)
   into this res/raw/ folder.
2. Delete this README_PLACEHOLDER.txt (optional).
3. Uncomment the  // playEmergencySound()  line in onNotificationPosted().

NOTE: Until a real emergency_tone file exists here, leave playEmergencySound()
commented out — otherwise the project will fail to compile because the
R.raw.emergency_tone resource won't exist.

Files in res/raw/ must be lowercase, start with a letter, and contain only
letters, digits, and underscores.
