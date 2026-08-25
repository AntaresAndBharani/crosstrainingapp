<!-- unit-test-evidence -->
### :test_tube: Unit Test Results

| Total | Passed | Failed | Skipped | Elapsed |
|---|---|---|---|---|
| 3 | 1 | 1 | 1 | 0.09s |

**Status:** ❌ 1 test failed.

#### Failures

**com.example.RedTest > testFailingMethod**

**Message:** `expected:<1> but was:<2>`

<details>
<summary>Stack Trace</summary>

```
java.lang.AssertionError: expected:<1> but was:<2>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:647)
	at app/src/test/java/com/example/RedTest.kt:42
	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
```

</details>

