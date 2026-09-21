//
//  CameraAuthorizationProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import AVFoundation
import VisionKit

enum CameraAccess: Equatable {
    case notDetermined
    case authorized
    case denied
    case unsupported
}

protocol CameraAuthorizationProviderProtocol {
    var status: CameraAccess { get }
    func requestAccess() async -> Bool
}

struct CameraAuthorizationProvider: CameraAuthorizationProviderProtocol {

    // MARK: - Properties

    var status: CameraAccess {
        // DataScannerViewController.isSupported is main-actor isolated in the SDK; this provider
        // is only ever read from main-thread SwiftUI/view-model code, so assumeIsolated bridges it
        // into this nonisolated getter without forcing @MainActor onto every caller in the chain.
        let isSupported = MainActor.assumeIsolated { DataScannerViewController.isSupported }
        guard isSupported else { return .unsupported }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .notDetermined:
            return .notDetermined
        case .authorized:
            return .authorized
        case .denied, .restricted:
            return .denied
        @unknown default:
            return .denied
        }
    }

    // MARK: - Functions

    func requestAccess() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .video)
    }
}

#if DEBUG
struct CameraAuthorizationProviderFake: CameraAuthorizationProviderProtocol {
    var status: CameraAccess = .authorized
    var requestAccessResult = true

    func requestAccess() async -> Bool { requestAccessResult }
}
#endif
