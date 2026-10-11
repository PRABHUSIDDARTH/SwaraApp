import SwiftUI
import SwaraCore

struct QueueView: View {
    @Environment(\.palette) var p
    @Environment(\.dismiss) var dismiss
    @Environment(PlayerController.self) var player

    var body: some View {
        NavigationStack {
            List {
                if let cur = player.current {
                    Section("Now Playing") {
                        SongRow(song: cur).listRowBackground(Color.clear)
                    }
                }
                Section("Up Next") {
                    if player.upNext.isEmpty {
                        Text("Nothing queued").foregroundStyle(p.textTertiary).listRowBackground(Color.clear)
                    }
                    ForEach(Array(player.upNext.enumerated()), id: \.element.id) { i, s in
                        Button { player.jump(toUpNext: i) } label: { SongRow(song: s) }
                            .buttonStyle(.plain)
                            .listRowBackground(Color.clear)
                    }
                    .onMove { player.moveInQueue(from: $0, to: $1) }
                    .onDelete { player.removeFromQueue(at: $0) }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(AtmosphereBackground())
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .bottomBar) {
                    Button("Clear Queue", role: .destructive) { player.clearQueue(); dismiss() }
                        .disabled(player.current == nil)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
