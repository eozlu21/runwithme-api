# User Recommendation System

This document explains how the RunWithMe user recommendation system works, from high-level concepts to implementation details.

---

## High-Level Overview

The recommendation system suggests running partners based on **two main factors**:

1. **Route Similarity (50%)** - How similar are the routes you run compared to another user?
2. **Preference Similarity (50%)** - How well do your survey preferences match?

Before calculating scores, users are **pre-filtered** by:
- Location (same city or country)
- Schedule compatibility (preferred days and time of day)
- Gender preference (if enabled)

---

## How It Works (Bird's Eye View)

```
┌─────────────────────────────────────────────────────────────────┐
│                    RECOMMENDATION FLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. USER REQUESTS RECOMMENDATIONS                               │
│           │                                                     │
│           ▼                                                     │
│  2. PRE-FILTER CANDIDATES                                       │
│     ├── Location (city → country → all)                         │
│     ├── Schedule (day + time compatibility)                     │
│     └── Gender (if matchGenderPreference = true)                │
│           │                                                     │
│           ▼                                                     │
│  3. FOR EACH CANDIDATE, CALCULATE:                              │
│     ├── Route Similarity (ML inference) ────► 50% weight        │
│     └── Preference Similarity (exact match) ─► 50% weight       │
│           │                                                     │
│           ▼                                                     │
│  4. SORT BY COMBINED SCORE (descending)                         │
│           │                                                     │
│           ▼                                                     │
│  5. RETURN PAGINATED RESULTS                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Route Similarity (Deep Dive)

### What It Measures
Route similarity determines how alike two users' running routes are based on:
- **Route Shape** - The actual geometry/path of the route
- **Distance** - How far the route goes
- **Pace** - Running speed (min/km)
- **Terrain** - Type of environment (urban, park, trail, etc.)

### How It's Calculated

The system uses a **Machine Learning model** to compare routes:

```
┌──────────────┐     ┌──────────────┐
│   Route A    │     │   Route B    │
│  (User 1)    │     │  (User 2)    │
└──────┬───────┘     └──────┬───────┘
       │                    │
       ▼                    ▼
┌──────────────────────────────────┐
│     Convert to ML Format         │
│  - Geometry (GeoJSON LineString) │
│  - Distance (meters)             │
│  - Pace (min/km)                 │
│  - Terrain (default: urban)      │
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│   Python Inference Service       │
│                                  │
│  1. Generate 64x64 route image   │
│  2. Extract feature embeddings   │
│  3. Compute cosine similarity    │
│                                  │
│  Returns:                        │
│  - overall: 85.2  ◄── We use this│
│  - shape: 78.5                   │
│  - metadata: 92.1                │
│  - pace: 95.0                    │
│  - distance: 88.3                │
└──────────────┬───────────────────┘
               │
               ▼
        Similarity Score
           (0-100)
```

### Aggregation Across Route Pairs

When comparing two users, we calculate the **average similarity** across ALL route pairs:

```
User A has routes: [R1, R2]
User B has routes: [R3, R4, R5]

Comparisons made:
  R1 vs R3 → 75.0
  R1 vs R4 → 82.0
  R1 vs R5 → 68.0
  R2 vs R3 → 71.0
  R2 vs R4 → 85.0
  R2 vs R5 → 79.0

Average = (75 + 82 + 68 + 71 + 85 + 79) / 6 = 76.7
```

### Edge Cases
| Scenario | Behavior |
|----------|----------|
| User has no routes | Return 50.0 (baseline) |
| Route has no geometry | Skip that route pair |
| ML inference fails | Return 50.0 (baseline) |

---

## Preference Similarity (Deep Dive)

### What It Measures
Preference similarity compares users' survey responses across 10 categories:

| Preference | Weight | Values |
|------------|--------|--------|
| Social Vibe | 15% | silent, social |
| Experience Level | 12% | beginner, amateur, intermediate, professional |
| Activity Type | 12% | walking, hiking, leisure, competitive |
| Motivation | 12% | mental, weightloss, training, socializing |
| Preferred Day | 12% | monday-sunday, weekday, weekend, any |
| Intensity | 8% | high, steady |
| Coaching Style | 8% | pusher, companion |
| Time of Day | 8% | early_bird, morning, lunch, afternoon, evening, night, any |
| Match Gender | 8% | true, false |
| Music Preference | 5% | headphone, nature |

### How It's Calculated

Each preference is compared using **exact matching**:

```
User A: socialVibe = "social"
User B: socialVibe = "social"
Match: YES → 100%

User A: experienceLevel = "amateur"
User B: experienceLevel = "professional"
Match: NO → 0%
```

**Special handling for schedule preferences:**
- `"any"` matches everything
- `"weekday"` matches monday-friday
- `"weekend"` matches saturday-sunday

### Final Score
```
Overall = Σ (matchPercent × weight)

Example:
  socialVibe:     100% × 0.15 = 15.0
  experienceLevel: 0% × 0.12 =  0.0
  activityType:  100% × 0.12 = 12.0
  ...

Total: 72.5%
```

---

## Pre-Filters

Before scoring, candidates are filtered to reduce the search space:

### 1. Location Filter
```
LocationFilterLevel:
  CITY    → Same city (fallback to country if no city)
  COUNTRY → Same country
  NONE    → No location filtering
```

### 2. Schedule Compatibility
Filters out users with incompatible schedules:
- If you prefer "weekday", exclude users who only run on "weekend"
- `"any"` is always compatible

### 3. Gender Preference
If the requesting user has `matchGenderPreference = true`:
- Only show candidates with the **same pronouns** value
- Pronouns: `he/him`, `she/her`, `they/them`

### 4. Friend Exclusion
Existing friends are always excluded from recommendations.

---

## Combined Score Formula

```
Combined Score = (Route Similarity × 0.5) + (Preference Similarity × 0.5)
```

Example:
```
Route Similarity:      76.7
Preference Similarity: 72.5

Combined = (76.7 × 0.5) + (72.5 × 0.5)
         = 38.35 + 36.25
         = 74.6
```

---

## API Endpoints

### Get Recommendations
```http
GET /api/v1/recommendations/users?page=0&size=10&locationLevel=CITY
Authorization: Bearer <token>
```

**Response:**
```json
{
  "content": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440001",
      "username": "runner_jane",
      "profilePic": "https://...",
      "combinedScore": 74.6,
      "routeSimilarityScore": 76.7,
      "preferenceSimilarityScore": 72.5,
      "routePairCount": 6,
      "hasRoutes": true,
      "hasSurvey": true
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 45,
  "totalPages": 5
}
```

### Get Detailed Similarity
```http
GET /api/v1/recommendations/users/{targetUserId}/similarity
Authorization: Bearer <token>
```

**Response:**
```json
{
  "targetUserId": "550e8400-e29b-41d4-a716-446655440001",
  "targetUsername": "runner_jane",
  "combinedScore": 74.6,
  "routeSimilarity": {
    "averageScore": 76.7,
    "pairCount": 6,
    "topPairs": [
      { "route1Id": 1, "route2Id": 5, "similarity": 85.0 },
      { "route1Id": 2, "route2Id": 4, "similarity": 82.0 }
    ]
  },
  "preferenceSimilarity": {
    "social_vibe": { "user1Value": "social", "user2Value": "social", "isMatch": true },
    "experience_level": { "user1Value": "amateur", "user2Value": "professional", "isMatch": false }
  },
  "isFriend": false
}
```

---

## Technical Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         KOTLIN BACKEND                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  UserRecommendationController                                       │
│         │                                                           │
│         ▼                                                           │
│  UserRecommendationService                                          │
│         │                                                           │
│         ├──► UserProfileRepository (location queries)               │
│         ├──► FriendshipRepository (friend exclusion)                │
│         ├──► RouteRepository (user routes)                          │
│         ├──► SurveyResponseRepository (user preferences)            │
│         ├──► UserPreferenceService (preference matching)            │
│         │                                                           │
│         └──► RouteSimilarityService.compareTwoRoutes()              │
│                      │                                              │
│                      ▼                                              │
│              ┌───────────────────┐                                  │
│              │  Python Process   │                                  │
│              │  (ProcessBuilder) │                                  │
│              │                   │                                  │
│              │  inference_script │                                  │
│              │  modular_features │                                  │
│              │  model.pt         │                                  │
│              └───────────────────┘                                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `UserRecommendationController.kt` | REST endpoints |
| `UserRecommendationService.kt` | Core recommendation algorithm |
| `RouteSimilarityService.kt` | ML inference bridge |
| `UserPreferenceService.kt` | Survey preference matching |
| `inference_script.py` | Python ML inference |
| `modular_features.py` | Feature extraction & model |
| `model.pt` | Trained route similarity model |

---

## Performance Considerations

1. **Route comparison is O(n×m)** where n and m are route counts per user
2. **Python inference has overhead** (~100-500ms per comparison)
3. **Pre-filtering reduces candidates** before expensive comparisons
4. **Results are paginated** to limit response size

For production with many users, consider:
- Pre-computing route embeddings at upload time
- Caching similarity scores
- Background batch processing for recommendations
