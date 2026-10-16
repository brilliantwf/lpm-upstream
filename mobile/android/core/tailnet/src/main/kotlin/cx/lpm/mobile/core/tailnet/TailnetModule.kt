package cx.lpm.mobile.core.tailnet

import cx.lpm.mobile.core.data.client.TailnetRouter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface TailnetModule {
    @Binds
    fun builtInTailscale(impl: UnavailableTailscale): BuiltInTailscale

    /** Fills `:core:data`'s optional router, so connections route through the node. */
    @Binds
    fun tailnetRouter(tailscale: BuiltInTailscale): TailnetRouter
}
