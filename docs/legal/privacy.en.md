# Memory Privacy Policy

Version of October 14, 2026. Draft: before publishing, have it reviewed by a lawyer in Armenia and fill in the details in square brackets. The Armenian version is the main one; the Russian and English versions are translations, and the Armenian text prevails in case of discrepancy.

## 1. Who processes the data

The operator (processor) of personal data is [Name: LLC "…" / sole proprietor …], taxpayer ID (ՀՎՀՀ) [number], address in the Republic of Armenia: [address]. Questions about data: [privacy@domain]. [Data protection officer: name, email, if appointed.]

This policy applies to the Memory app on Android and iPhone and to the website [https://memory-site]. It has been prepared under the Law of the Republic of Armenia "On Protection of Personal Data".

## 2. What data we process and why

- **Account**: username, name, password (only its hash is stored, using PBKDF2), and, once the server is connected, a phone number for sign-in. Email, if you added it in Security; recovery codes are stored only as hashes. Why: to create your account, protect sign-in and restore access if you forget your password.
- **Profile**: photo, bio, city, birthday, website, interests, whatever you fill in yourself. Why: to show your profile to others according to your privacy settings.
- **Content**: posts, photos, videos, voice messages, PRISM, stickers, comments, polls, messages, check-ins, shelf entries, showcase products, #tags and @mentions, stories, highlights, reposts, a post's place and people tagged in photos (you can only tag friends; a tagged person sees the post under “Tags”). Why: to show them to the people you addressed them to.
- **Live streams**: video, audio and the stream chat are delivered to viewers while the stream is on and are not stored after it ends.
- **Stories** are visible to friends for 24 hours (or only to “Close friends” — only you see that list), then stay in your archive. The author sees who viewed the story and who sent a heart.
- **Calls**: voice and video go to the other person during the call and are not recorded. The call history keeps who, when, voice or video, and duration. Who can call you is set in Settings → Privacy.
- **Sign-in and devices**: device name and type, when it was last used, and sign-in tokens (stored encrypted on the phone). Why: to show you your devices and let you sign out of any of them.
- **Groups and chats**: group members and roles are visible to group members; “read” and “typing” are visible to the people you chat with. Message requests from people who aren't your friends don't show them you've read anything until you accept.
- **Hidden words and filter** run on the phone: your hidden-words list is stored with your privacy settings (so it works on all your phones) and is never shown to anyone.
- **Geolocation**: only when you send a location yourself, or mark a treasure, a find or an outing in Together. There is no background geolocation.
- **Technical data**: device model and OS version, language, push notification token. Why: to deliver notifications.
- **Crash reports and usage statistics**: only if you separately turn them on in Settings → Privacy → Crashes and statistics (off by default). A crash report contains the error type and the place in the code, without your posts or chats; emails, usernames and long numbers are removed from it. Statistics are daily event counters ("posts published: 3") without any texts. Why: to fix crashes and understand which features are used.
- **Actions in the app** (invitations, posts, check-ins): to build personal suggestions.

The legal basis is the consent you give when you register, and the performance of the Terms of Use. We do not process biometric or genetic data: cutting out objects for stickers runs on your phone and does not identify people. We do not sell data and do not show ads based on it.

## 3. Where data is stored

Currently (before the server launches), your account, posts and conversations are stored only on your phone. Messages in chats where you turned on end-to-end encryption are encrypted on the phone with a key known only to the two phones: the server will store and relay them only in encrypted form and cannot read them. Disappearing messages are deleted for both participants when the chosen period ends. After the server launches, data will be stored on servers in [country and cloud provider name]. If that country is not on the list of countries with adequate data protection maintained by the Personal Data Protection Agency, the transfer is based on your explicit consent or on standard contractual clauses registered with the Agency.

Content saved for offline use — the feed you've viewed, the queue of unsent items and unfinished uploads — is kept in a database on your phone, separate for each account. Photos and videos are compressed before sending: the compressed copy is kept in the app's cache and deleted once sent; the original on your phone is not changed. To clear saved content: Settings → Data and network.

## 4. Who data is shared with

- **Other users**: what you publish, according to your privacy settings.
- **Services that certain features cannot work without** (transfer outside Armenia, to which you give explicit consent when registering):
  - Google Firebase Cloud Messaging (USA): device token for delivering push notifications on Android;
  - Apple Push Notification service (USA): device token for push notifications on iPhone;
  - GIPHY (USA): GIF search text and IP address when you search for a GIF;
  - Jamendo (Luxembourg) and Openverse (USA): search queries in the media library;
  - OpenStreetMap (United Kingdom): map tile requests on Android;
  - Sentry (Functional Software, Inc., USA): crash reports and usage statistics, **only with your separate consent** in Settings → Privacy;
  - LiveKit Cloud (LiveKit, Inc., USA; servers in the country nearest to you): relays the video and audio of calls and live streams between participants; does not record them.
  - Tailscale (USA), if the Memory server is exposed to the internet via Tailscale Funnel: relays the encrypted connection (TLS ends on our server) and cannot see its contents; it sees connection IP addresses.
- **Government authorities**: only in the cases and in the manner expressly established by the law of the Republic of Armenia.

## 5. How long we keep data

For as long as the account exists. After the account is deleted, data is deleted within 30 days, except for what the law requires us to keep longer.

## 6. Your rights

You can download a copy of your data at any time: Settings → Privacy → Download my data, an archive with your profile, posts, chats, groups and settings. You can find out what data we process and who it has been shared with, correct it, request its deletion or the end of its processing, withdraw your consent and delete your account: Settings → Security → Delete account, or by email to [privacy@domain]. We will respond within 5 business days. If personal suggestions affect you, you can ask for them to be reviewed by a human (see "How recommendations work"). You can file a complaint with the Personal Data Protection Agency of the Ministry of Justice of the Republic of Armenia.

## 7. Age

Memory is for people aged 16 and over. If you learn that an account was created by someone under 16, write to [privacy@domain] and we will delete it.

## 8. Security and breaches

Passwords and recovery codes are stored only as hashes, the session and the chat encryption key are kept in the system keystore (Android Keystore, iOS Keychain), and connections to the server use TLS. In case of a leak from the information system, we will immediately notify the Personal Data Protection Agency and the police, as the law requires, publish a notice about it and, if the risk is high, notify you right away.

## 9. Changes

A new version is published on the website and in the app; in case of significant changes, the app will ask you to accept it again.
