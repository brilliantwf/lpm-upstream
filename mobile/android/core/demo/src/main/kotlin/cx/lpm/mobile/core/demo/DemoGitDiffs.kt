package cx.lpm.mobile.core.demo

// The diffs the git review screen shows for the demo's changes.

internal fun genericAddedDiff(path: String): String = """
    diff --git a/$path b/$path
    new file mode 100644
    index 0000000..b2d61a4
    --- /dev/null
    +++ b/$path
    @@ -0,0 +1,4 @@
    +export function demo() {
    +  return true
    +}
    +
""".trimIndent()

internal fun genericModifiedDiff(path: String): String = """
    diff --git a/$path b/$path
    index 3f1c2aa..9b04d1e 100644
    --- a/$path
    +++ b/$path
    @@ -1,5 +1,6 @@
     import { config } from "./config"

    -const enabled = false
    +const enabled = true
    +const retries = 3

     export { config }
""".trimIndent()

private val demoCheckoutFormDiff = """
    diff --git a/components/CheckoutForm.tsx b/components/CheckoutForm.tsx
    index 4a5c2de..8f13a9b 100644
    --- a/components/CheckoutForm.tsx
    +++ b/components/CheckoutForm.tsx
    @@ -3,6 +3,8 @@
     import { Button } from "@/components/ui/button"
     import { Input } from "@/components/ui/input"
     import { formatPrice } from "@/lib/format"
    +import { createPaymentIntent, type PaymentMethod } from "@/lib/payments"
    +import { PaymentMethodPicker } from "./PaymentMethodPicker"

     type CheckoutFormProps = {
       cart: Cart
    @@ -11,11 +13,21 @@ type CheckoutFormProps = {

     export function CheckoutForm({ cart, onComplete }: CheckoutFormProps) {
       const [email, setEmail] = useState("")
    +  const [method, setMethod] = useState<PaymentMethod>("card")
       const [submitting, setSubmitting] = useState(false)
    +  const [error, setError] = useState<string | null>(null)

       async function handleSubmit(event: React.FormEvent) {
         event.preventDefault()
         setSubmitting(true)
    -    await submitOrder({ email, items: cart.items })
    -    onComplete()
    +    setError(null)
    +    try {
    +      const intent = await createPaymentIntent(cart, method)
    +      await submitOrder({ email, intent, items: cart.items })
    +      onComplete()
    +    } catch {
    +      setError("Payment failed. Please try again.")
    +    } finally {
    +      setSubmitting(false)
    +    }
       }
    @@ -32,6 +44,8 @@ export function CheckoutForm({ cart, onComplete }: CheckoutFormProps) {
             onChange={(e) => setEmail(e.target.value)}
             required
           />
    +      <PaymentMethodPicker value={method} onChange={setMethod} />
    +      {error && <p className="text-sm text-red-600">{error}</p>}
           <Button type="submit" disabled={submitting}>
             {submitting ? "Placing order…" : `Pay ${'$'}{formatPrice(cart.total)}`}
           </Button>
""".trimIndent()

private val demoCheckoutPageDiff = """
    diff --git a/app/checkout/page.tsx b/app/checkout/page.tsx
    index c91e044..2b7a51c 100644
    --- a/app/checkout/page.tsx
    +++ b/app/checkout/page.tsx
    @@ -1,13 +1,19 @@
     import { CheckoutForm } from "@/components/CheckoutForm"
    +import { OrderSummary } from "@/components/OrderSummary"
     import { getCart } from "@/lib/cart"

    +export const metadata = { title: "Checkout" }
    +
     export default async function CheckoutPage() {
       const cart = await getCart()

       return (
    -    <main className="mx-auto max-w-lg px-4 py-8">
    -      <h1 className="text-2xl font-semibold">Checkout</h1>
    -      <CheckoutForm cart={cart} />
    +    <main className="mx-auto grid max-w-4xl gap-8 px-4 py-8 md:grid-cols-[1fr_360px]">
    +      <section>
    +        <h1 className="text-2xl font-semibold">Checkout</h1>
    +        <CheckoutForm cart={cart} />
    +      </section>
    +      <OrderSummary cart={cart} />
         </main>
       )
     }
""".trimIndent()

private val demoPaymentsDiff = """
    diff --git a/lib/payments.ts b/lib/payments.ts
    new file mode 100644
    index 0000000..7d1f2ab
    --- /dev/null
    +++ b/lib/payments.ts
    @@ -0,0 +1,22 @@
    +import type { Cart } from "./cart"
    +
    +export type PaymentMethod = "card" | "apple-pay"
    +
    +export type PaymentIntent = {
    +  id: string
    +  amount: number
    +  method: PaymentMethod
    +}
    +
    +export async function createPaymentIntent(
    +  cart: Cart,
    +  method: PaymentMethod,
    +): Promise<PaymentIntent> {
    +  const res = await fetch("/api/payments/intent", {
    +    method: "POST",
    +    headers: { "Content-Type": "application/json" },
    +    body: JSON.stringify({ amount: cart.total, method }),
    +  })
    +  if (!res.ok) throw new Error("Could not start the payment")
    +  return res.json()
    +}
""".trimIndent()

private val demoReadmeDiff = """
    diff --git a/README.md b/README.md
    index e4a10bd..f6c92d3 100644
    --- a/README.md
    +++ b/README.md
    @@ -6,7 +6,12 @@ ## Getting started

     ```sh
     npm install
     npm run dev
     ```

    +## Payments
    +
    +Checkout talks to the new payments module in `lib/payments.ts`. Set
    +`PAYMENT_API_KEY` in `.env.local` before testing the flow locally.
    +
     ## Deployment
""".trimIndent()

internal val demoCannedDiffs = mapOf(
    "components/CheckoutForm.tsx" to demoCheckoutFormDiff,
    "app/checkout/page.tsx" to demoCheckoutPageDiff,
    "lib/payments.ts" to demoPaymentsDiff,
    "README.md" to demoReadmeDiff
)
