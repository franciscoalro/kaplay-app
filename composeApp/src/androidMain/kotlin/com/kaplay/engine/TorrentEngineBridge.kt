package com.kaplay.engine

// Single binary-compatibility seam to the vendored torrent engine AAR.
// The AAR ships with its original Java package; everything above this file
// uses only these aliases.
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioEventType
import com.nuvio.engine.NuvioStream
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode

typealias TorrentEngine = NuvioEngine
typealias TorrentEngineConfig = NuvioEngineConfig
typealias TorrentEventType = NuvioEventType
typealias TorrentStream = NuvioStream
typealias TorrentProfile = NuvioTorrentProfile
typealias TorrentUploadMode = NuvioUploadMode
