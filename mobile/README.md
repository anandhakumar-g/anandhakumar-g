# Single Point — Mobile App (MVP-1)

Expo SDK 57 · expo-router · React Native · TypeScript. One app, three roles (Resident,
Admin, Provider) plus a minimal Super Admin surface.

## Run

The backend must be running first (`../backend`, defaults to `http://localhost:18080`).

```bash
npm install

# phone (Expo Go) or emulator
npx expo start

# browser (also how Admin/Super Admin use it for MVP-1)
npx expo start --web
```

Point at a non-default backend with `EXPO_PUBLIC_API_URL`, e.g. for a real device on your LAN:

```bash
EXPO_PUBLIC_API_URL=http://192.168.1.20:18080 npx expo start
```

Android emulator automatically uses `10.0.2.2`; iOS simulator and web use `localhost`.

Dev login: enter any phone number, then use the **dev code** shown on the OTP screen (the
backend prints/returns it while `sp.otp.dev-mode=true`). The local backend seeds:

| Role | Phone |
|---|---|
| Resident | *(any new number)* — then join a community with an invite code |
| Admin | `+919000000101` (Green Meadows) |
| Provider | `+919000000301` (Sparky Electricals) |
| Super Admin | `+919000000000` |

The Green Meadows admin invite code is printed in the backend startup banner.

## Layout

```
app/
  _layout.tsx        providers + session Gate (routes by onboarding state + role)
  (auth)/            login · otp · profile · join · pending
  (resident)/        home · raise · tickets · ticket/[id] · settings   (tabs)
  (admin)/           queue · community · providers · ticket/[id] · settings   (tabs)
  (provider)/        jobs · job/[id] · settings   (tabs)
  (super)/           tenant health + onboard community + invite admin
src/
  theme/             tokens (5 themes) + ThemeProvider (tenant brand override)
  api/               typed client, endpoint groups, config (base URL resolution)
  store/             SessionProvider (token in AsyncStorage, /me hydration)
  components/         Themed primitives, Button, Field, Bits, TicketRow, TicketDetail, SettingsScreen
  hooks/             useAsync
```

## Notes

- Adding a theme = one entry in `src/theme/tokens.ts` — no screen changes.
- Push notifications register best-effort on a physical device; unavailable on web.
- MVP-1 has no dedicated web admin console — the Expo app on `--web` covers Admin/Super Admin.
