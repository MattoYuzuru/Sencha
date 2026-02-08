import UIKit

final class AppStore {
    struct ModelItem {
        let id: String
        let name: String
        let category: String
        let provider: String
        let requiresNetwork: Bool
    }

    final class ChatItem {
        let id: String
        let title: String
        let model: ModelItem
        var messages: [ChatMessage]

        init(id: String, title: String, model: ModelItem, messages: [ChatMessage]) {
            self.id = id
            self.title = title
            self.model = model
            self.messages = messages
        }
    }

    struct ChatMessage {
        let id: String
        let role: String
        var content: String
    }

    private(set) var models: [ModelItem]
    private(set) var chats: [ChatItem]
    var selectedModel: ModelItem
    private(set) var isLoadingModels: Bool
    private(set) var modelsError: String?

    init() {
        let local = ModelItem(
            id: "local-text",
            name: "Local Text",
            category: "Чат",
            provider: "Local",
            requiresNetwork: false
        )
        let ollama = ModelItem(
            id: "llama3",
            name: "Ollama: llama3",
            category: "Чат",
            provider: "Ollama",
            requiresNetwork: true
        )
        self.models = [local, ollama]
        self.selectedModel = local
        self.chats = []
        self.isLoadingModels = false
        self.modelsError = nil
    }

    func createChat(title: String) -> ChatItem {
        let chat = ChatItem(
            id: "chat-\(Date().timeIntervalSince1970)",
            title: title.isEmpty ? selectedModel.name : title,
            model: selectedModel,
            messages: []
        )
        chats.insert(chat, at: 0)
        return chat
    }

    func selectModel(_ model: ModelItem) {
        selectedModel = model
    }
}
