// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.cli;

import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.filters.Filter;
import com.intellij.ide.util.projectWizard.ModuleNameLocationSettings;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.javascript.nodejs.util.NodePackage;
import com.intellij.lang.javascript.boilerplate.NpmPackageProjectGenerator;
import com.intellij.lang.javascript.boilerplate.NpxPackageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.text.SemVer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import icons.AngularJSIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 */
public class AngularCLIProjectGenerator extends NpmPackageProjectGenerator {

  public static final String PACKAGE_NAME = "@angular/cli";
  private static final Logger LOG = Logger.getInstance(AngularCLIProjectGenerator.class);
  private static final Pattern NPX_PACKAGE_PATTERN =
    Pattern.compile("npx --package @angular/cli(?:@([0-9]+\\.[0-9]+\\.[0-9a-zA-Z-.]+))? ng");
  private static final Pattern VALID_NG_APP_NAME = Pattern.compile("[a-zA-Z][0-9a-zA-Z]*(-[a-zA-Z][0-9a-zA-Z]*)*");

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Angular CLI";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "The Angular CLI makes it easy to create an application that already works, right out of the box. It already follows our best practices!";
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return AngularJSIcons.Angular2;
  }

  @Override
  protected void customizeModule(@NotNull VirtualFile baseDir, ContentEntry entry) {
    if (entry != null) {
      AngularJSProjectConfigurator.excludeDefault(baseDir, entry);
    }
  }

  @Override
  @NotNull
  protected String[] generatorArgs(@NotNull Project project, @NotNull VirtualFile baseDir) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  @NotNull
  protected String[] generatorArgs(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Settings settings) {
    AngularCLIProjectSettings ngSettings = (AngularCLIProjectSettings)settings;
    List<String> result = new ArrayList<>();
    result.add("new");
    result.add(baseDir.getName());
    CommandLineTokenizer tokenizer = new CommandLineTokenizer(ngSettings.myOptions);
    while (tokenizer.hasMoreTokens()) {
      result.add(tokenizer.nextToken());
    }

    if (isPackageGreaterOrEqual(settings.myPackage, 7, 0, 0)) {
      if (!result.contains("--defaults")) {
        result.add("--defaults");
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  @SuppressWarnings("SameParameterValue")
  private static boolean isPackageGreaterOrEqual(NodePackage pkg, int major, int minor, int patch) {
    SemVer ver = null;
    if (pkg.getName().equals(PACKAGE_NAME)) {
      ver = pkg.getVersion();
    }
    else {
      Matcher m = NPX_PACKAGE_PATTERN.matcher(pkg.getSystemIndependentPath());
      if (m.matches()) {
        ver = SemVer.parseFromText(m.group(1));
      }
    }
    return ver == null
           || ver.isGreaterOrEqualThan(major, minor, patch);
  }

  @NotNull
  @Override
  protected Filter[] filters(@NotNull Project project, @NotNull VirtualFile baseDir) {
    return new Filter[]{new AngularCLIFilter(project, baseDir.getParent().getPath())};
  }

  @NotNull
  @Override
  protected String executable(@NotNull NodePackage pkg) {
    return ng(pkg.getSystemDependentPath());
  }

  @NotNull
  public static String ng(String path) {
    return path + File.separator + "bin" + File.separator + "ng";
  }

  @Override
  @NotNull
  protected String packageName() {
    return PACKAGE_NAME;
  }

  @Override
  @NotNull
  protected String presentablePackageName() {
    return "Angular &CLI:";
  }

  @NotNull
  @Override
  protected List<NpxPackageDescriptor.NpxCommand> getNpxCommands() {
    return Collections.singletonList(new NpxPackageDescriptor.NpxCommand(PACKAGE_NAME, "ng"));
  }

  @Override
  protected String validateProjectPath(@NotNull String path) {
    return Optional.ofNullable(validateFolderName(path, "Project"))
      .orElseGet(() -> super.validateProjectPath(path));
  }

  @SuppressWarnings("deprecation")
  @NotNull
  @Override
  public GeneratorPeer<Settings> createPeer() {
    return new AngularCLIProjectGeneratorPeer();
  }

  @NotNull
  @Override
  protected File workingDir(Settings settings, @NotNull VirtualFile baseDir) {
    return VfsUtilCore.virtualToIoFile(baseDir).getParentFile();
  }


  @NotNull
  @Override
  protected Runnable postInstall(@NotNull Project project,
                                 @NotNull VirtualFile baseDir,
                                 File workingDir) {
    return () -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
      super.postInstall(project, baseDir, workingDir).run();
      AngularCliUtil.createRunConfigurations(project, baseDir);
    });
  }

  @Nullable
  private static String validateFolderName(String path, String label) {
    String fileName = PathUtil.getFileName(path);
    if (!VALID_NG_APP_NAME.matcher(fileName).matches()) {
      return XmlStringUtil.wrapInHtml(
        label + " name '" + fileName + "' is not valid. " + label + " name must " +
        "start with a letter, and must contain only alphanumeric characters or dashes. " +
        "When adding a dash the segment after the dash must also start with a letter."
      );
    }
    return null;
  }


  private class AngularCLIProjectGeneratorPeer extends NpmPackageGeneratorPeer {

    private TextAccessor myContentRoot;

    private SchematicOptionsTextField myOptionsTextField;

    @Override
    protected JPanel createPanel() {
      final JPanel panel = super.createPanel();
      ComboBox<String> schematicsCollectionCombo = new ComboBox<>(new String[]{"default", "@ngrx/schematics"});

      LabeledComponent component = LabeledComponent.create(schematicsCollectionCombo, "Schematics collection:");
      component.setAnchor((JComponent)panel.getComponent(0));
      component.setLabelLocation(BorderLayout.WEST);
      panel.add(component);

      myOptionsTextField = new SchematicOptionsTextField(ProjectManager.getInstance().getDefaultProject(),
                                                         Collections.emptyList());
      myOptionsTextField.setEnabled(true);
      myOptionsTextField.setVariants(Collections.singletonList(new Option("test")));

      component = LabeledComponent.create(myOptionsTextField, "Additional parameters:");
      component.setAnchor((JComponent)panel.getComponent(0));
      component.setLabelLocation(BorderLayout.WEST);
      panel.add(component);

      return panel;
    }

    @Override
    public void buildUI(@NotNull SettingsStep settingsStep) {
      super.buildUI(settingsStep);
      final ModuleNameLocationSettings field = settingsStep.getModuleNameLocationSettings();
      if (field != null) {
        myContentRoot = new TextAccessor() {
          @Override
          public void setText(@NotNull String text) {
            field.setModuleContentRoot(text);
          }

          @Override
          @NotNull
          public String getText() {
            return field.getModuleContentRoot();
          }
        };
      }
      ((GistManagerImpl)GistManager.getInstance()).invalidateData();
      settingsStep.addSettingsField(UIUtil.replaceMnemonicAmpersand("Additional parameters:"), myOptionsTextField);
      getPackageField().addSelectionListener(this::nodePackageChanged);
      nodePackageChanged(getPackageField().getSelected());
    }

    @NotNull
    @Override
    public Settings getSettings() {
      return new AngularCLIProjectSettings(super.getSettings(), myOptionsTextField.getText());
    }

    @Nullable
    @Override
    public ValidationInfo validate() {
      final ValidationInfo info = super.validate();
      if (info != null) {
        return info;
      }
      if (myContentRoot != null) {
        String message = validateFolderName(myContentRoot.getText(), "Content root folder");
        if (message != null) {
          return new ValidationInfo(message);
        }
      }
      return null;
    }

    private void nodePackageChanged(NodePackage nodePackage) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<Option> options = Collections.emptyList();
        if (nodePackage.getSystemIndependentPath().endsWith("/node_modules/@angular/cli")) {
          VirtualFile localFile = StandardFileSystems.local().findFileByPath(
            nodePackage.getSystemDependentPath());
          if (localFile != null) {
            localFile = localFile.getParent().getParent().getParent();
            try {
              options = SchematicsLoader.INSTANCE
                .load(ProjectManager.getInstance().getDefaultProject(), localFile, true, false)
                .stream()
                .filter(s -> "ng-new".equals(s.getName()))
                .findFirst()
                .map(schematic -> {
                  List<Option> list = ContainerUtil.newArrayList(schematic.getOptions());
                  list.add(createOption("verbose", "Boolean", false, "Adds more details to output logging."));
                  list.add(createOption("collection", "String", null, "Schematics collection to use"));
                  Collections.sort(list, Comparator.comparing(Option::getName));
                  return list;
                })
                .orElse(Collections.emptyList());
            }
            catch (Exception e) {
              LOG.error("Failed to load schematics", e);
            }
          }
        }
        myOptionsTextField.setVariants(options);
      });
    }

    private Option createOption(String name, String type, Object defaultVal, String description) {
      Option res = new Option(name);
      res.setType(type);
      res.setDefault(defaultVal);
      res.setDescription(description);
      return res;
    }
  }

  private static class AngularCLIProjectSettings extends Settings {

    @NotNull
    public final String myOptions;

    AngularCLIProjectSettings(@NotNull Settings settings,
                              @NotNull String options) {
      super(settings.myInterpreterRef, settings.myPackage);
      myOptions = options;
    }
  }
}
