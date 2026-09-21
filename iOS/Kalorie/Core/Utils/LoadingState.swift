//
//  LoadingState.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

enum LoadingState<T> {
    case idle
    case loading
    case loaded(T)
    /// Rendered only by the auth root (`AuthStateObserver`) with a Retry button; features
    /// report failures through `alertItem` instead. See ADR 0018.
    case error(Error?)
}

extension LoadingState where T == Void {
    static let loaded: LoadingState<Void> = .loaded(())
}

extension LoadingState {
    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }
}
