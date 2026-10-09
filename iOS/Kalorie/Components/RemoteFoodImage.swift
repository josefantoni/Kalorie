//
//  RemoteFoodImage.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.10.2026.
//

import SwiftUI

enum RemoteFoodImagePhase {
    case loading
    case success(Image)
    case failure
}

struct RemoteFoodImage<Content: View>: View {

    // MARK: - Properties

    let url: URL
    let loader: RemoteImageLoader
    let content: (RemoteFoodImagePhase) -> Content

    @State private var phase: RemoteFoodImagePhase = .loading

    // MARK: - Init

    init(
        url: URL,
        loader: RemoteImageLoader = RemoteImageLoader(),
        @ViewBuilder content: @escaping (RemoteFoodImagePhase) -> Content
    ) {
        self.url = url
        self.loader = loader
        self.content = content
    }

    // MARK: - Body

    var body: some View {
        content(phase)
            .task(id: url) { await load() }
    }

    // MARK: - Functions

    private func load() async {
        phase = .loading
        do {
            phase = .success(Image(uiImage: try await loader.load(url)))
        } catch is CancellationError {
            return
        } catch {
            if (error as? URLError)?.code == .cancelled { return }
            Log.error(error, category: Constants.LogCategory.storage)
            phase = .failure
        }
    }
}
