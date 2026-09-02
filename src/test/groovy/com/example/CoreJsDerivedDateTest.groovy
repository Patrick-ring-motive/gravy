package com.example

/** Groovy vectors derived from current core-js Date now, ISO, JSON, and primitive modules. */
class CoreJsDerivedDateTest extends GravyTestCase {
    void testNowReturnsEpochMilliseconds() {
        long before = System.currentTimeMillis()
        long now = JavaScriptDate.now()
        long after = System.currentTimeMillis()

        assert now >= before && now <= after
    }

    void testJsonUsesIsoAndInvalidDateUsesNull() {
        assert new JavaScriptDate(0).toJSON() == '1970-01-01T00:00:00Z'
        assert new JavaScriptDate('invalid').toJSON() == null
    }
}
