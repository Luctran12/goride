# Three-Word Location Mobile Integration

Backend status: ready on branch `codex/word-location-mobile`.

This feature wraps the custom Python coordinate/three-word service. React Native calls the GoRide backend only; it must not call Flask directly.

## 1. Runtime configuration

Set these variables on the GoRide backend:

```properties
THREE_WORD_LOCATION_ENABLED=true
THREE_WORD_LOCATION_BASE_URL=http://localhost:5000
THREE_WORD_LOCATION_TIMEOUT_SECONDS=3
```

`THREE_WORD_LOCATION_BASE_URL` is the Python service root. GoRide appends `/api/to-words` or `/api/to-coordinate`.

When GoRide runs in Docker, `localhost` points to the GoRide container. Use the Python service/container hostname instead, for example:

```properties
THREE_WORD_LOCATION_BASE_URL=http://three-word-location:5000
```

The provider is disabled by default. This keeps unrelated backend features healthy when the Python service is not deployed.

## 2. API contract

Both endpoints require a valid JWT for role `PASSENGER` or `DRIVER`.

Required headers:

```http
Authorization: Bearer <accessToken>
Accept: application/json
X-Request-Id: <uuid>
```

### Coordinates to three words

```http
GET /api/v1/locations/to-words?lat=10.7769&lng=106.7009
```

GoRide sends `lng` to mobile-facing APIs and translates it to Flask's `lon` parameter internally.

### Three words to coordinates

```http
GET /api/v1/locations/to-coordinate?address=hoa.la.cay
```

Accepted input examples:

```text
hoa.la.cay
///hoa.la.cay
 Hoa . La . Cay
hoa.khuon mat.cay
```

The backend trims whitespace, converts inner whitespace to `_`, and lowercases the words before calling Python. Responses convert provider `_` back to spaces for display, for example `hoa.khuon_mat.cay` is returned as `hoa.khuon mat.cay`.

### Success response

Both endpoints return the same shape:

```json
{
  "success": true,
  "data": {
    "lat": 10.7769,
    "lng": 106.7009,
    "words": ["hoa", "khuon mat", "cay"],
    "wordAddress": "hoa.khuon mat.cay",
    "bounds": {
      "southwest": {
        "lat": 10.7768,
        "lng": 106.7008
      },
      "northeast": {
        "lat": 10.7770,
        "lng": 106.7010
      }
    }
  },
  "message": "OK",
  "timestamp": "2026-07-27T14:00:00Z"
}
```

`lat` and `lng` are always the source of truth for map, routing, fare, matching, and booking.

## 3. Shared React Native client

```ts
export type Coordinate = {
  lat: number;
  lng: number;
};

export type ThreeWordBounds = {
  southwest: Coordinate;
  northeast: Coordinate;
};

export type ThreeWordLocation = Coordinate & {
  words: [string, string, string];
  wordAddress: string;
  bounds: ThreeWordBounds;
};

type ApiSuccess<T> = {
  success: true;
  data: T;
  message: string;
  timestamp: string;
};

type ApiFailure = {
  success: false;
  error: {
    code: string;
    message: string;
    details: Record<string, unknown>;
  };
  requestId?: string;
  timestamp: string;
};

export class GoRideApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details: Record<string, unknown>,
    public readonly requestId?: string,
  ) {
    super(message);
  }
}

async function get<T>(
  apiBaseUrl: string,
  path: string,
  accessToken: string,
): Promise<T> {
  const requestId = crypto.randomUUID();
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
      "X-Request-Id": requestId,
    },
  });

  const payload = (await response.json()) as ApiSuccess<T> | ApiFailure;
  if (!response.ok || !payload.success) {
    const failure = payload as ApiFailure;
    throw new GoRideApiError(
      response.status,
      failure.error.code,
      failure.error.message,
      failure.error.details,
      failure.requestId,
    );
  }
  return payload.data;
}

export function getThreeWords(
  apiBaseUrl: string,
  accessToken: string,
  coordinate: Coordinate,
): Promise<ThreeWordLocation> {
  const query = new URLSearchParams({
    lat: String(coordinate.lat),
    lng: String(coordinate.lng),
  });
  return get(
    apiBaseUrl,
    `/api/v1/locations/to-words?${query.toString()}`,
    accessToken,
  );
}

export function resolveThreeWords(
  apiBaseUrl: string,
  accessToken: string,
  address: string,
): Promise<ThreeWordLocation> {
  const query = new URLSearchParams({ address });
  return get(
    apiBaseUrl,
    `/api/v1/locations/to-coordinate?${query.toString()}`,
    accessToken,
  );
}
```

If the React Native runtime does not expose `crypto.randomUUID()`, use the UUID library already used by the app.

## 4. Passenger map flow

Recommended state:

```ts
type ThreeWordLookupState =
  | { status: "idle" }
  | { status: "loading"; coordinate: Coordinate }
  | { status: "resolved"; location: ThreeWordLocation }
  | { status: "error"; coordinate: Coordinate; code: string };
```

Flow:

1. User taps the map and the app updates the selected pin.
2. Show the selected street address, coordinate, and button `Lay 3 tu`.
3. On button press, set `loading`, disable duplicate presses, and call `getThreeWords`.
4. On success, keep the original pin and show `wordAddress` with copy/share actions.
5. If the user moves the pin, clear the previous three-word result because it belongs to the old coordinate.
6. Booking continues to send the existing `{ lat, lng, address }` object.

Do not replace the normal street `address` with `wordAddress` automatically. Keep the three-word value as display/share metadata unless the product later adds a dedicated booking column.

The passenger must still be able to book by coordinate when the three-word provider is unavailable.

## 5. Driver lookup flow

Flow:

1. Driver taps `Tim bang 3 tu`.
2. Open a bottom sheet with one input and a search action.
3. Submit on keyboard search or button press; disable duplicate submits while loading.
4. Call `resolveThreeWords`.
5. Move the camera to returned `lat/lng`, render a preview marker, and optionally draw the returned cell bounds.
6. Show the display-friendly `wordAddress` and actions `Xem tuyen` or `Dan duong`.
7. Require explicit confirmation before using the preview as any editable destination.

This lookup is independent from active trip pickup/dropoff. It must never silently overwrite the destination of an accepted or in-progress trip.

## 6. Error handling

| Error code | HTTP | Mobile behavior |
| --- | ---: | --- |
| `VALIDATION_ERROR` | 400 | Highlight invalid/missing coordinate input. |
| `WORD_LOCATION_INVALID_ADDRESS` | 400 | Keep the sheet open and show the expected `word.word.word` format. |
| `WORD_LOCATION_NOT_FOUND` | 404 | Keep the entered value and ask the driver to check the words. |
| `WORD_LOCATION_OUT_OF_BOUNDS` | 422 | Explain that the selected point is outside the supported map; keep normal map selection available. |
| `WORD_LOCATION_PROVIDER_UNAVAILABLE` | 503 | Hide or disable the optional feature for the session; do not block booking/navigation. |
| `WORD_LOCATION_PROVIDER_ERROR` | 502 | Show retry; retain pin/input and attach `requestId` to support logs. |
| `TOKEN_EXPIRED`, `TOKEN_INVALID` | 401 | Run the app's existing refresh-token flow, then retry once. |
| `FORBIDDEN` | 403 | Hide the action for unsupported roles and do not retry. |
| `RATE_LIMIT_EXCEEDED` | 429 | Honor `Retry-After`; prevent rapid repeated requests. |

Do not display `providerMessage` directly as final UI text. Localize by stable `error.code`.

## 7. Release checklist

- Python service is reachable from the GoRide runtime network.
- `THREE_WORD_LOCATION_ENABLED=true` is set only after the provider health check succeeds.
- Passenger can select a pin and retrieve/copy display-friendly three words.
- Moving the passenger pin invalidates the old three-word result.
- Driver can enter mixed case, spaces around dots, and compound words.
- Driver result first appears as a preview and does not mutate active trip state.
- 400, 404, 422, 502, 503, 401, and 429 states are rendered without losing user input.
- Booking estimate/create still use `lat`, `lng`, and normal street `address`.
- Logs can be found with the request's `X-Request-Id`.
