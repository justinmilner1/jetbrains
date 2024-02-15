package com.sourcegraph.cody.context.ui

import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.util.Alarm
import com.sourcegraph.cody.config.DialogValidationUtils
import com.sourcegraph.cody.context.RemoteRepoUtils
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.vcs.convertGitCloneURLToCodebaseNameOrError
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.annotations.NotNull

class AddRepositoryDialog(private val project: Project, private val addAction: (String) -> Unit) :
    DialogWrapper(project) {

  private val repoUrlInputField = TextFieldWithAutoCompletion.create(project, listOf(), false, null)

  init {
    init()
    title = CodyBundle.getString("context-panel.add-repo-dialog.title")
    setOKButtonText("Add")
    setValidationDelay(100)
  }

  override fun doValidateAll(): List<ValidationInfo> {
    fun validateNonEmpty() =
        DialogValidationUtils.custom(
            repoUrlInputField,
            CodyBundle.getString("context-panel.add-repo-dialog.error-empty-url")) {
              repoUrlInputField.text.isNotBlank()
            }

    fun validateValidUrl() =
        DialogValidationUtils.custom(
            repoUrlInputField,
            CodyBundle.getString("context-panel.add-repo-dialog.error-invalid-url")) {
              val url =
                  if (repoUrlInputField.text.startsWith("http")) repoUrlInputField.text
                  else "http://" + repoUrlInputField.text
              runCatching { URL(url) }.isSuccess
            }

    fun validateRepoExists() =
        DialogValidationUtils.custom(
            repoUrlInputField,
            CodyBundle.getString("context-panel.add-repo-dialog.error-no-repo")) {
              val codebaseName = convertGitCloneURLToCodebaseNameOrError(repoUrlInputField.text)
              val repo =
                  RemoteRepoUtils.getRepository(project, codebaseName)
                      .completeOnTimeout(null, 2, TimeUnit.SECONDS)
                      .get()
              repo != null
            }

    return listOfNotNull(validateNonEmpty() ?: validateValidUrl() ?: validateRepoExists())
  }

  override fun getValidationThreadToUse(): Alarm.ThreadToUse {
    return Alarm.ThreadToUse.POOLED_THREAD
  }

  override fun doOKAction() {
    addAction(repoUrlInputField.text)
    close(OK_EXIT_CODE, true)
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = JPanel(GridBagLayout())
    val rightSidePanel = JPanel(GridBagLayout())

    // TODO: we can provide repository suggestions using `provider.setItems` method
    val completionProvider: TextFieldWithAutoCompletionListProvider<String> =
        object : TextFieldWithAutoCompletionListProvider<String>(listOf()) {
          @NotNull
          override fun getLookupString(@NotNull s: String): String {
            return s
          }
        }

    myPreferredFocusedComponent = repoUrlInputField
    repoUrlInputField.setPreferredWidth(350)
    repoUrlInputField.installProvider(completionProvider)
    repoUrlInputField.requestFocusInWindow()
    repoUrlInputField.addDocumentListener(
        object : DocumentListener {
          override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
            initValidation()
          }
        })

    fun constraints(gridx: Int, gridy: Int, ipadx: Int = 0, ipady: Int = 0): GridBagConstraints {
      val c = GridBagConstraints()
      c.gridx = gridx
      c.gridy = gridy
      c.ipadx = ipadx
      c.ipady = ipady
      c.anchor = GridBagConstraints.FIRST_LINE_START
      return c
    }

    mainPanel.add(
        JLabel(CodyBundle.getString("context-panel.add-repo-dialog.url-input-label")),
        constraints(gridx = 0, gridy = 0, ipady = 10))
    mainPanel.add(rightSidePanel, constraints(gridx = 1, gridy = 0, ipadx = 10))

    rightSidePanel.add(repoUrlInputField, constraints(gridx = 0, gridy = 0))
    rightSidePanel.add(
        JLabel(CodyBundle.getString("context-panel.add-repo-dialog.url-input-help")),
        constraints(gridx = 0, gridy = 1, ipadx = 10, ipady = 10))

    return mainPanel
  }
}