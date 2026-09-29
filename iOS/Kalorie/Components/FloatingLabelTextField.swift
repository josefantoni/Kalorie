//
//  FloatingLabelTextField.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.09.2026.
//

import SwiftUI

enum FloatingLabelTextFieldMessage {
    case hint(String)
    case warning(String)
    case error(String)

    var text: String {
        switch self {
        case .hint(let text), .warning(let text), .error(let text):
            return text
        }
    }

    var color: Color {
        switch self {
        case .hint:
            return .hint
        case .warning:
            return .warning
        case .error:
            return .error
        }
    }
}

struct FloatingLabelTextField<Field: Hashable>: View {

    // MARK: - Properties

    let title: String
    var placeholder: String?
    @Binding var text: String
    var message: FloatingLabelTextFieldMessage?
    var keyboardType: UIKeyboardType = .default
    var isHighlighted: Bool = false
    var minHeight: CGFloat = 44

    private let externalFocus: FocusState<Field?>.Binding?
    private let focusValue: Field

    @FocusState private var ownFocus: Field?
    @Environment(\.isEnabled) private var isEnabled
    @State private var fieldHeight: CGFloat = 0
    @State private var titleHeight: CGFloat = 0
    @State private var floatedTitleHeight: CGFloat = 0
    @State private var hasAppeared = false

    // MARK: - Init

    init(
        title: String,
        placeholder: String? = nil,
        text: Binding<String>,
        message: FloatingLabelTextFieldMessage? = nil,
        keyboardType: UIKeyboardType = .default,
        isHighlighted: Bool = false,
        minHeight: CGFloat = 44,
        focus: FocusState<Field?>.Binding,
        equals focusValue: Field
    ) {
        self.title = title
        self.placeholder = placeholder
        self._text = text
        self.message = message
        self.keyboardType = keyboardType
        self.isHighlighted = isHighlighted
        self.minHeight = minHeight
        self.externalFocus = focus
        self.focusValue = focusValue
    }

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: Self.messageSpacing) {
            fieldView
            messageView
        }
        .opacity(isEnabled ? 1 : Self.disabledOpacity)
        .alignmentGuide(VerticalAlignment.center) { _ in
            fieldHeight / 2
        }
        .task {
            hasAppeared = true
        }
    }

    // MARK: - Functions

    private var focus: FocusState<Field?>.Binding {
        externalFocus ?? $ownFocus
    }

    private var isFocused: Bool {
        focus.wrappedValue == focusValue
    }

    private var isTitleFloated: Bool {
        isFocused || !text.isEmpty
    }

    private var isError: Bool {
        if case .error = message {
            return true
        }
        return false
    }

    private var titleColor: Color {
        if isError {
            return .error
        }
        return .hint
    }

    private var floatedScale: CGFloat {
        titleHeight > 0 ? floatedTitleHeight / titleHeight : 1
    }

    private var titleOffset: CGFloat {
        let floatedTop = Self.verticalPadding + (floatedTitleHeight - titleHeight) / 2
        let centredTop = (fieldHeight - titleHeight) / 2
        return isTitleFloated ? floatedTop : centredTop
    }

    private var fieldView: some View {
        ZStack(alignment: .leading) {
            placeholderView
            TextField("", text: $text)
                .font(.body)
                .fontWeight(isHighlighted ? .bold : .regular)
                .keyboardType(keyboardType)
                .focused(focus, equals: focusValue)
                .accessibilityLabel(title)
                .accessibilityHint(message?.text ?? "")
        }
        .padding(.top, floatedTitleHeight)
        .padding(.vertical, Self.verticalPadding)
        .frame(minHeight: minHeight, alignment: .bottom)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            Text(title)
                .font(.caption)
                .lineLimit(1)
                .hidden()
                .onGeometryChange(for: CGFloat.self) { proxy in
                    proxy.size.height
                } action: { height in
                    floatedTitleHeight = height.rounded()
                }
        }
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { height in
            fieldHeight = height.rounded()
        }
        .overlay(alignment: .topLeading) {
            floatingTitle
        }
        .contentShape(Rectangle())
        .onTapGesture {
            focus.wrappedValue = focusValue
        }
        .animation(hasAppeared ? Self.animation : nil, value: isFocused)
    }

    private var floatingTitle: some View {
        Text(title)
            .font(.body)
            .lineLimit(1)
            .minimumScaleFactor(Self.minimumScaleFactor)
            .foregroundStyle(titleColor)
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.height
            } action: { height in
                titleHeight = height.rounded()
            }
            .scaleEffect(isTitleFloated ? floatedScale : 1, anchor: .leading)
            .offset(y: titleOffset)
                .animation(hasAppeared ? Self.animation : nil, value: isTitleFloated)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    @ViewBuilder private var placeholderView: some View {
        if let placeholder {
            Text(placeholder)
                .font(.body)
                .lineLimit(1)
                .minimumScaleFactor(Self.minimumScaleFactor)
                .foregroundStyle(Color.hint)
                .opacity(isFocused && text.isEmpty ? 1 : 0)
                .animation(hasAppeared ? Self.animation : nil, value: isFocused)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }

    @ViewBuilder private var messageView: some View {
        if let message {
            Text(message.text)
                .font(.caption)
                .foregroundStyle(message.color)
                .padding(.bottom, Self.messageBottomPadding)
                .accessibilityHidden(true)
        }
    }

    private static var animation: Animation { .easeInOut(duration: 0.2) }
    private static var verticalPadding: CGFloat { 4 }
    private static var messageSpacing: CGFloat { 0 }
    private static var messageBottomPadding: CGFloat { 3 }
    private static var disabledOpacity: Double { 0.5 }
    private static var minimumScaleFactor: CGFloat { 0.5 }
}

extension FloatingLabelTextField where Field == Bool {
    init(
        title: String,
        placeholder: String? = nil,
        text: Binding<String>,
        message: FloatingLabelTextFieldMessage? = nil,
        keyboardType: UIKeyboardType = .default,
        isHighlighted: Bool = false,
        minHeight: CGFloat = 44
    ) {
        self.title = title
        self.placeholder = placeholder
        self._text = text
        self.message = message
        self.keyboardType = keyboardType
        self.isHighlighted = isHighlighted
        self.minHeight = minHeight
        self.externalFocus = nil
        self.focusValue = true
    }
}

// MARK: - Preview

private struct FloatingLabelTextFieldPreview: View {

    let title: String
    var placeholder: String?
    var text: String = ""
    var message: FloatingLabelTextFieldMessage?
    var isHighlighted: Bool = false
    var isDisabled: Bool = false
    var isFocusedOnAppear: Bool = false
    var isReadOnly: Bool = false

    @State private var editableText = ""
    @FocusState private var focusedField: Bool?

    var body: some View {
        FloatingLabelTextField(
            title: title,
            placeholder: placeholder,
            text: isReadOnly ? .constant(text) : $editableText,
            message: message,
            isHighlighted: isHighlighted,
            focus: $focusedField,
            equals: true
        )
        .disabled(isDisabled)
        .onAppear {
            editableText = text
            focusedField = isFocusedOnAppear ? true : nil
        }
    }
}

private struct FloatingLabelTextFieldPreviewPair: View {

    let content: FloatingLabelTextFieldPreview

    var body: some View {
        VStack(spacing: 0) {
            ForEach([ColorScheme.light, ColorScheme.dark], id: \.self) { scheme in
                content
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.systemBackground))
                    .environment(\.colorScheme, scheme)
            }
        }
    }
}

#Preview("Empty") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name"))
}

#Preview("Focused") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name", placeholder: "Low-fat cottage cheese", isFocusedOnAppear: true))
}

#Preview("Filled") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name", text: "Cottage cheese"))
}

#Preview("Disabled") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name", text: "Cottage cheese", isDisabled: true))
}

#Preview("Read-only filled") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food barcode", text: "8592345678901", isDisabled: true, isReadOnly: true))
}

#Preview("Hint") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food barcode", message: .hint("no barcode")))
}

#Preview("Warning") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food barcode", message: .warning("Barcode is missing")))
}

#Preview("Error") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name", text: "Ch", message: .error("Name is too short")))
}

#Preview("Highlighted") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name", text: "Cottage cheese", isHighlighted: true))
}

#Preview("Largest Dynamic Type") {
    FloatingLabelTextFieldPreviewPair(content: FloatingLabelTextFieldPreview(title: "Food name", text: "Cottage cheese", message: .hint("Hint text")))
        .dynamicTypeSize(.accessibility5)
}
