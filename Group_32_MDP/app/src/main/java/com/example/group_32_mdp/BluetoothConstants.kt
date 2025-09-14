// BluetoothConstants.kt
package com.example.group_32_mdp

import java.util.UUID

class BluetoothConstants private constructor() {
    companion object {
        /** Standard Serial Port Profile UUID */
        val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
