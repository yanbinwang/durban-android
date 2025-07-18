package com.example.durban

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Controller(
    val enable: Boolean,
    val rotation: Boolean,
    val rotationTitle: Boolean,
    val scale: Boolean,
    val scaleTitle: Boolean
) : Parcelable {

    companion object {
        @JvmStatic
        fun newBuilder(): Builder {
            return Builder()
        }

        class Builder {
            private var enable = true
            private var rotation = true
            private var rotationTitle = true
            private var scale = true
            private var scaleTitle = true

            fun enable(enable: Boolean): Builder {
                this.enable = enable
                return this
            }

            fun rotation(rotation: Boolean): Builder {
                this.rotation = rotation
                return this
            }

            fun rotationTitle(rotationTitle: Boolean): Builder {
                this.rotationTitle = rotationTitle
                return this
            }

            fun scale(scale: Boolean): Builder {
                this.scale = scale
                return this
            }

            fun scaleTitle(scaleTitle: Boolean): Builder {
                this.scaleTitle = scaleTitle
                return this
            }

            fun build(): Controller {
                return Controller(enable, rotation, rotationTitle, scale, scaleTitle)
            }
        }

    }

}