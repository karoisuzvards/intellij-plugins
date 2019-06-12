package com.intellij.lang.javascript.linter.tslint.config;

import com.intellij.javascript.nodejs.PackageJsonData;
import com.intellij.lang.javascript.JSBundle;
import com.intellij.lang.javascript.linter.JSLinterConfiguration;
import com.intellij.lang.javascript.linter.JSLinterDescriptor;
import com.intellij.lang.javascript.linter.JSLinterGuesser;
import com.intellij.lang.javascript.linter.JSLinterUtil;
import com.intellij.lang.javascript.linter.tslint.TslintUtil;
import com.intellij.lang.javascript.linter.tslint.codestyle.TsLintCodeStyleImporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.intellij.lang.javascript.linter.JSLinterConfigFileUtil.findDistinctConfigInContentRoots;

/**
 * @author Irina.Chernushina on 11/24/2016.
 */
public final class TsLintDescriptor extends JSLinterDescriptor {
  public static final String PACKAGE_NAME = "tslint";

  @NotNull
  @Override
  public String getDisplayName() {
    return JSBundle.message("settings.javascript.linters.tslint.configurable.name");
  }

  @Override
  public String packageName() {
    return PACKAGE_NAME;
  }

  @Override
  public boolean hasConfigFiles(@NotNull Project project) {
    return TslintUtil.hasConfigFiles(project);
  }

  @Override
  public boolean usesLanguageService() {
    return true;
  }

  @Override
  public boolean enable(@NotNull Project project) {
    final PackageJsonData packageJson = JSLinterUtil.getTopLevelPackageJsonData(project);
    // skip if there is tslint-language-service
    if (packageJson != null && packageJson.getAllDependencies().contains("tslint-language-service")) return false;
    return super.enable(project);
  }

  @Override
  public void importSettings(@NotNull Project project, @NotNull JSLinterGuesser.EnableCase enableCase) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    VirtualFile config = findDistinctConfigInContentRoots(project, Arrays.asList(TslintUtil.CONFIG_FILE_NAMES));
    if (config == null) return;

    PsiFile file = PsiManager.getInstance(project).findFile(config);
    if (file == null) return;

    new TsLintCodeStyleImporter(true).importConfigFileWhenToolInstalled(file);
  }

  @NotNull
  @Override
  public Class<? extends JSLinterConfiguration> getConfigurationClass() {
    return TsLintConfiguration.class;
  }
}
