import com.nachidel.bambu.api.BambuCloudClient
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class ClientCreationTest {

    @Test
    fun `should create client`() {

        val client = BambuCloudClient()

        client shouldNotBe null

    }

}