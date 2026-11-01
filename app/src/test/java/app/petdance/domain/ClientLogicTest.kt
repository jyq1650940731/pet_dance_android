package app.petdance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientLogicTest {
    @Test
    fun phoneAcceptsMainlandAndStripsPrefix() {
        assertEquals("13800138000", normalizePhone("13800138000"))
        assertEquals("13800138000", normalizePhone("+86 138-0013-8000"))
        assertNull(normalizePhone("12345"))
    }

    @Test
    fun petRankPutsMatchingScopeFirst() {
        assertEquals(0, petRank("cat", "cat"))
        assertEquals(1, petRank("cat", "any"))
        assertEquals(2, petRank("cat", "dog"))
        assertEquals(1, petRank("other", "dog"))
        assertEquals(1, petRank(null, "cat"))
    }

    @Test
    fun loopbackMediaUrlFollowsApiHost() {
        val signed = "http://127.0.0.1:8080/_local-storage/object/results/a.mp4?exp=1&sig=ab"
        val rewritten = rewriteMediaUrl(signed, "http://10.0.2.2:8080/")
        assertEquals("http://10.0.2.2:8080/_local-storage/object/results/a.mp4?exp=1&sig=ab", rewritten)
        val cdn = "https://cdn.example.com/a.mp4"
        assertEquals(cdn, rewriteMediaUrl(cdn, "http://10.0.2.2:8080/"))
    }

    @Test
    fun generationStepsAdvanceWithStatus() {
        assertEquals(StepMark.Done, generationStep("generating", 0))
        assertEquals(StepMark.Done, generationStep("generating", 1))
        assertEquals(StepMark.Current, generationStep("generating", 2))
        assertEquals(StepMark.Pending, generationStep("generating", 3))
        assertEquals("可播放", worksLabel("succeeded", true, null))
        assertEquals("该作品已下架", worksLabel("succeeded", false, "该作品已下架"))
    }

    @Test
    fun sampleSizeShrinksLargePhotos() {
        assertEquals(1, app.petdance.media.decodeSampleSize(720, 1280, 2560))
        assertEquals(2, app.petdance.media.decodeSampleSize(4000, 3000, 2560))
    }
}
