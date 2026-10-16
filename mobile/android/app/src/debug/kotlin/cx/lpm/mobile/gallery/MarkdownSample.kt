package cx.lpm.mobile.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.web.MarkdownFileReader
import cx.lpm.mobile.core.web.MarkdownWebView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A README in the Markdown preview: headings, a highlighted code block with its
 * Copy button, a table, an image read through the file reader (drawn here; the
 * app fetches it from the Mac) and one the "Mac" doesn't have, which shows its
 * alt text.
 */
@Composable
internal fun MarkdownSample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reader = remember { SampleImageReader(context.cacheDir) }
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemBackground)
    ) {
        LpmTopBar(title = "README.md", leading = { BackButton(onBack) })
        MarkdownWebView(
            text = SAMPLE_README,
            project = "storefront",
            path = "README.md",
            fileReader = reader,
            onOpenFile = {
                Toast.makeText(context, "Open ${it.joinToString()}", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            darkTheme = LpmTheme.colors.isDark,
            onOpenUrl = { Toast.makeText(context, "Open $it", Toast.LENGTH_SHORT).show() }
        )
    }
}

private class SampleImageReader(private val dir: File) : MarkdownFileReader {
    override suspend fun read(project: String, candidates: List<String>): File? =
        withContext(Dispatchers.IO) {
            if ("docs/checkout.png" !in candidates) return@withContext null
            File(dir, "checkout.png").also { file ->
                val bitmap = createBitmap(640, 280)
                Canvas(bitmap).apply {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.shader =
                        LinearGradient(
                            0f,
                            0f,
                            640f,
                            280f,
                            0xFF0A84FF.toInt(),
                            0xFF5E5CE6.toInt(),
                            Shader.TileMode.CLAMP
                        )
                    drawRoundRect(0f, 0f, 640f, 280f, 32f, 32f, paint)
                    paint.shader = null
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.textSize = 56f
                    drawText("Checkout", 48f, 160f, paint)
                }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
}

private val SAMPLE_README = """
    # storefront

    A **Next.js** shop with a Stripe checkout. See [the setup guide](docs/setup.md),
    the [API notes](#api) or [lpm.cx](https://lpm.cx).

    ![Checkout screen](docs/checkout.png)

    ## Scripts

    | Command | What it does |
    | --- | --- |
    | `npm run dev` | Starts the dev server on port 3000 |
    | `npm test` | Runs the unit tests |
    | `npm run build` | Builds for production |

    ## API

    ```ts
    export async function createPaymentIntent(cart: Cart) {
      const amount = cart.items.reduce((sum, item) => sum + item.price, 0)
      return stripe.paymentIntents.create({ amount, currency: "usd" })
    }
    ```

    > Keys live in `.env.local`, never in the repo.

    - [x] Cart
    - [ ] Gift cards

    ![Architecture diagram](docs/missing.png)
""".trimIndent()
