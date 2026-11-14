@file:Suppress("UNUSED_PARAMETER")
package app.inphotomaniak

import androidx.compose.runtime.Composable

/** No Cast actions or placeholders are displayed in the FOSS edition. */
@Composable internal fun CastButton(controller: CastController) = Unit
@Composable internal fun CastControls(controller: CastController) = Unit
@Composable internal fun CastMenuItem(controller: CastController,onSelected: ()->Unit) = Unit
