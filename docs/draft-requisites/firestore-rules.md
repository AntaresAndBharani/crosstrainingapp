# Firestore Security Rules — Athlete Body Weight Tracker

## Context
CrossTraining mobile app syncs athlete user data partitioned by environment and user UID:
`/environments/{env}/users/{userId}/data/{collectionName}`

Supported collections under `/environments/{env}/users/{userId}/data`:
- `exercises`
- `routines`
- `sessions`
- `cycle_goals`
- `rep_maxes`
- `weight_entries`

## Required Firestore Security Rule Specification

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Multi-tenant environment root
    match /environments/{env} {
      
      // User partition
      match /users/{userId} {
        
        // Owner-only user document read/write
        allow read, write: if request.auth != null && request.auth.uid == userId;
        
        // User subcollections under /data
        match /data/{collectionName} {
          allow read, write: if request.auth != null && request.auth.uid == userId;
        }
        
        // Explicit match for weight_entries collection document
        match /data/weight_entries {
          allow read, write: if request.auth != null && request.auth.uid == userId;
        }
      }
    }
  }
}
```

## Security Rationale
1. **Owner-Only Isolation**: Strictly verifies `request.auth.uid == userId` to prevent cross-tenant data leaks and unauthorized access to athlete biometric/weight logs.
2. **Environment Scoping**: Rules are scoped under `/environments/{env}`, ensuring complete isolation between `snapshot` and `production` tiers.
3. **Document-Level Payload**: The document contains a top-level `list` array containing weight entries and tombstones (`date`, `weightKg`, `notes`, `updatedAtMillis`, `deletedAtMillis`).
