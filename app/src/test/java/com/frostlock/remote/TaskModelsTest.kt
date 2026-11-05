package com.frostlock.remote

import com.frostlock.remote.data.WindowsTaskSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskModelsTest {
    @Test fun parsesTaskSnapshotFromWindows() {
        val snapshot = WindowsTaskSnapshot.fromJson(
            JSONObject(
                """
                {
                  "sampledAtMs": 1700000000000,
                  "sampleMilliseconds": 350,
                  "machineName": "DESKTOP-1",
                  "userName": "alex",
                  "controllerElevated": true,
                  "totalCpuPercent": 37.5,
                  "totalMemoryBytes": 17179869184,
                  "usedMemoryBytes": 8589934592,
                  "networkReceiveBytesPerSec": 2048,
                  "networkSendBytesPerSec": 512,
                  "processCount": 2,
                  "appCount": 1,
                  "connectionCount": 4,
                  "processes": [
                    {
                      "pid": 4242,
                      "name": "notepad",
                      "displayName": "Notepad",
                      "publisher": "Microsoft Corporation",
                      "version": "10.0",
                      "path": "C:\\Windows\\notepad.exe",
                      "iconKey": "0123456789abcdef",
                      "cpuPercent": 2.5,
                      "workingSetBytes": 10485760,
                      "privateBytes": 8388608,
                      "diskReadBytes": 4096,
                      "diskWriteBytes": 2048,
                      "diskReadBytesPerSec": 0,
                      "diskWriteBytesPerSec": 10.5,
                      "connectionCount": 2,
                      "remoteEndpoints": ["1.1.1.1:443", "8.8.8.8:53"],
                      "threadCount": 9,
                      "sessionId": 1,
                      "startedAtMs": 1700000000000,
                      "mainWindowTitle": "Untitled - Notepad",
                      "hasWindow": true,
                      "elevated": false,
                      "protected": false,
                      "protectedReason": null
                    },
                    {
                      "pid": 4,
                      "name": "System",
                      "displayName": "System",
                      "cpuPercent": 0.4,
                      "workingSetBytes": 4096,
                      "privateBytes": 0,
                      "diskReadBytes": 0,
                      "diskWriteBytes": 0,
                      "diskReadBytesPerSec": 0,
                      "diskWriteBytesPerSec": 0,
                      "connectionCount": 0,
                      "remoteEndpoints": [],
                      "threadCount": 120,
                      "sessionId": 0,
                      "startedAtMs": 0,
                      "hasWindow": false,
                      "elevated": null,
                      "protected": true,
                      "protectedReason": "Windows core process"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals("DESKTOP-1", snapshot.machineName)
        assertEquals(37.5, snapshot.totalCpuPercent, 0.001)
        assertTrue(snapshot.controllerElevated)
        assertEquals(2, snapshot.processes.size)

        val notepad = snapshot.processes.first()
        assertEquals(4242, notepad.pid)
        assertEquals("Notepad", notepad.displayName)
        assertEquals("0123456789abcdef", notepad.iconKey)
        assertEquals(2.5, notepad.cpuPercent, 0.001)
        assertEquals(2, notepad.connectionCount)
        assertEquals(listOf("1.1.1.1:443", "8.8.8.8:53"), notepad.remoteEndpoints)
        assertTrue(notepad.hasWindow)
        assertEquals(false, notepad.elevated)
        assertFalse(notepad.protected)

        val system = snapshot.processes.last()
        assertNull(system.elevated)
        assertTrue(system.protected)
        assertEquals("Windows core process", system.protectedReason)
    }
}
