# Cloud sync: how the pieces fit

Cloud sync is optional. Without signing in, the app never touches the network.

```
Android app  --HTTPS-->  Cloud (Cloudflare Worker + D1)  <--HTTPS--  Web dashboard
(this repo)              heartwood-api.dazaike.workers.dev            (Vercel)
                                  ^
                                  |  read-only MCP (/mcp/<token>)
                          ChatGPT, Claude, other MCP clients
```

| Part | Repo | Role |
|---|---|---|
| Android app | [Dazaike/heartwood](https://github.com/Dazaike/heartwood/tree/cloud-sync) (this repo, branch `cloud-sync`) | Reads Health Connect or Samsung Health and uploads metric/SI readings |
| Cloud | [Dazaike/heartwood-cloud](https://github.com/Dazaike/heartwood-cloud) | Accounts, storage, REST API, remote MCP server. API reference in its `docs/API.md` |
| Web dashboard | [Dazaike/heartwood-dashboard](https://github.com/Dazaike/heartwood-dashboard) | Charts, goals, imports; creates the MCP tokens. Live at `https://health-dashboard-one-sandy-80.vercel.app` |

## Set up

1. Create an account in the app (Settings > Cloud sync) or on the dashboard. The server address defaults to `https://heartwood-api.dazaike.workers.dev` and can be changed.
2. The app backfills up to 365 days, then re-sends the last 3 days about every 15 minutes while online (and on "Sync now").
3. Sign in to the dashboard with the same account to see the data.
4. For ChatGPT or Claude: dashboard Settings > AI access > create a token, then paste the `.../mcp/hwm_...` URL into the client's custom connector. That URL is a secret; revoke it from the same page.

## Notes

- The wire format is always metric/SI (km, kg, cm, L, mg/dL), whatever unit the app shows. The dashboard converts on read.
- Goals flow from the app and the dashboard into one shared `goals` blob (distance in miles, hydration in fl oz). The app only pushes them.
