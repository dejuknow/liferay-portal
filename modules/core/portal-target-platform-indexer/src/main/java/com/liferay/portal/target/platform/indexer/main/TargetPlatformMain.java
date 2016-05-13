/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.target.platform.indexer.main;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Verifier;
import aQute.bnd.osgi.resource.CapabilityBuilder;

import com.liferay.portal.bootstrap.ModuleFrameworkImpl;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.target.platform.indexer.Indexer;
import com.liferay.portal.target.platform.indexer.internal.PathUtil;
import com.liferay.portal.util.FastDateFormatFactoryImpl;
import com.liferay.portal.util.FileImpl;
import com.liferay.portal.util.PropsImpl;
import com.liferay.portal.util.PropsValues;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;

import java.net.URI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.NativeNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Capability;
import org.osgi.service.indexer.ResourceIndexer;
import org.osgi.service.indexer.impl.RepoIndex;
import org.osgi.service.repository.ContentNamespace;

/**
 * @author Raymond Augé
 */
public class TargetPlatformMain implements Indexer {

	public static void main(String[] args) throws Exception {
		FastDateFormatFactoryUtil fastDateFormatFactoryUtil =
			new FastDateFormatFactoryUtil();

		fastDateFormatFactoryUtil.setFastDateFormatFactory(
			new FastDateFormatFactoryImpl());

		FileUtil fileUtil = new FileUtil();

		fileUtil.setFile(new FileImpl());

		Path tempPath = Files.createTempDirectory(null);

		File tempDir = tempPath.toFile();

		com.liferay.portal.util.PropsUtil.set(
			PropsKeys.MODULE_FRAMEWORK_STATE_DIR, tempDir.getCanonicalPath());

		PropsUtil.setProps(new PropsImpl());

		String[] moduleFrameworkInitialBundles = PropsUtil.getArray(
			PropsKeys.MODULE_FRAMEWORK_INITIAL_BUNDLES);

		for (int i = 0; i < moduleFrameworkInitialBundles.length; i++) {
			String moduleFrameworkInitialBundle =
				moduleFrameworkInitialBundles[i];

			if (moduleFrameworkInitialBundle.endsWith("@start")) {
				moduleFrameworkInitialBundles[i] =
					moduleFrameworkInitialBundle.substring(
						0, moduleFrameworkInitialBundle.length() - 6);
			}
		}

		com.liferay.portal.util.PropsUtil.set(
			PropsKeys.MODULE_FRAMEWORK_INITIAL_BUNDLES,
			StringUtil.merge(moduleFrameworkInitialBundles));

		File targetPlatformDir = new File(
			PropsValues.MODULE_FRAMEWORK_BASE_DIR, DIR_NAME_TARGET_PLATFORM);

		if (!targetPlatformDir.exists() && !targetPlatformDir.mkdirs()) {
			System.err.printf(
				"== Unable to create directory %s\n", targetPlatformDir);

			return;
		}

		TargetPlatformMain targetPlatformMain = new TargetPlatformMain(
			"com.liferay.target.platform", ReleaseInfo.getVersion());

		try {
			File indexFile = targetPlatformMain.index(targetPlatformDir);

			System.out.println("== Wrote index file " + indexFile);
		}
		finally {
			PathUtil.deltree(tempPath);
		}
	}

	public TargetPlatformMain(String bundleSymbolicName, String bundleVersion) {
		_bundleSymbolicName = bundleSymbolicName;
		_bundleVersion = bundleVersion;

		_config.put("compressed", "false");
		_config.put(
			"license.url", "https://www.liferay.com/downloads/ce-license");
		_config.put("pretty", "true");
		_config.put("repository.name", ReleaseInfo.getReleaseInfo());
		_config.put("stylesheet", "http://www.osgi.org/www/obr2html.xsl");
	}

	@Override
	public File index(File outputFile) throws Exception {
		Path tempPath = Files.createTempDirectory(null);

		File tempDir = tempPath.toFile();

		_config.put("root.url", tempDir.getCanonicalPath());

		_moduleFrameworkImpl.initFramework();

		_moduleFrameworkImpl.startFramework();

		Framework framework = _moduleFrameworkImpl.getFramework();

		BundleContext bundleContext = framework.getBundleContext();

		processBundle(bundleContext.getBundle(0));

		Manifest manifest = new Manifest();

		Attributes attributes = manifest.getMainAttributes();

		attributes.putValue(
			Constants.BUNDLE_DESCRIPTION, ReleaseInfo.getReleaseInfo());
		attributes.putValue(
			Constants.BUNDLE_COPYRIGHT,
			"Copyright (c) 2000-present All rights reserved.");
		attributes.putValue(
			Constants.BUNDLE_LICENSE,
			"http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt");
		attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
		attributes.putValue(Constants.BUNDLE_SYMBOLICNAME, _bundleSymbolicName);
		attributes.putValue(Constants.BUNDLE_VENDOR, ReleaseInfo.getVendor());
		attributes.putValue(Constants.BUNDLE_VERSION, _bundleVersion);

		String exportPackage = StringUtil.replace(
			_packagesParamters.toString(), "version:Version", "version");

		attributes.putValue(Constants.EXPORT_PACKAGE, exportPackage);

		StringBundler sb = new StringBundler();

		for (Parameters parameter : _parametersList) {
			sb.append(parameter.toString());
			sb.append(",");
		}

		sb.setIndex(sb.index() - 1);

		String capabilities = sb.toString();

		attributes.putValue(Constants.PROVIDE_CAPABILITY, capabilities);

		Jar jar = new Jar("distro");

		jar.setManifest(manifest);

		try (Verifier verifier = new Verifier(jar)) {
			verifier.setProperty(
				Constants.FIXUPMESSAGES,
				"osgi.* namespace must not be specified with generic " +
					"capabilities");

			verifier.verify();

			verifier.getErrors();

			if (!verifier.isOk()) {
				List<String> errors = verifier.getErrors();

				sb = new StringBundler((errors.size() * 4) + 3);

				sb.append(TargetPlatformMain.class.getName());
				sb.append(" failed with {");

				for (String error : verifier.getErrors()) {
					sb.append("[");
					sb.append(error);
					sb.append("]");
					sb.append(",");
				}

				sb.setIndex(sb.index() - 1);

				sb.append("}");

				throw new Exception(sb.toString());
			}

			File jarFile = new File(
				tempPath.toFile(),
				_bundleSymbolicName + "-" + _bundleVersion + ".jar");

			jar.write(jarFile);

			Set<File> jarFiles = new LinkedHashSet<>();

			jarFiles.add(jarFile);

			for (String moduleFrameworkInitialBundle :
					PropsValues.MODULE_FRAMEWORK_INITIAL_BUNDLES) {

				addBundle(
					jarFiles, moduleFrameworkInitialBundle,
					PropsValues.MODULE_FRAMEWORK_BASE_DIR + "/static/",
					tempPath.toFile());
			}

			String[] autoDeployDirs = ArrayUtil.append(
				new String[] {PropsValues.MODULE_FRAMEWORK_PORTAL_DIR},
				PropsValues.MODULE_FRAMEWORK_AUTO_DEPLOY_DIRS);

			for (String autoDeployDir : autoDeployDirs) {
				File dir = new File(autoDeployDir);

				if (!dir.isDirectory() || !dir.canRead()) {
					continue;
				}

				File[] childFiles = dir.listFiles(
					new FilenameFilter() {

						@Override
						public boolean accept(File dir, String name) {
							return name.endsWith(".jar");
						}

					});

				for (File childFile : childFiles) {
					addBundle(jarFiles, childFile, tempPath.toFile());
				}
			}

			ResourceIndexer resourceIndexer = new RepoIndex();

			File tempIndexFile = new File(
				tempPath.toFile(),
				_bundleSymbolicName + "-" + _bundleVersion + "-index.xml");

			try (FileOutputStream fileOutputStream =
					new FileOutputStream(tempIndexFile)) {

				resourceIndexer.index(jarFiles, fileOutputStream, _config);
			}

			File indexFile = new File(outputFile, tempIndexFile.getName());

			Files.copy(
				tempIndexFile.toPath(), indexFile.toPath(),
				StandardCopyOption.COPY_ATTRIBUTES,
				StandardCopyOption.REPLACE_EXISTING);

			return indexFile;
		}
		finally {
			PathUtil.deltree(tempPath);

			_moduleFrameworkImpl.stopFramework(0);
		}
	}

	protected void addBundle(Set<File> jarFiles, File bundleFile, File tempDir)
		throws IOException {

		File jarFile = new File(tempDir, bundleFile.getName());

		Files.copy(
			bundleFile.toPath(), jarFile.toPath(),
			StandardCopyOption.COPY_ATTRIBUTES,
			StandardCopyOption.REPLACE_EXISTING);

		jarFiles.add(jarFile);
	}

	protected void addBundle(
			Set<File> jarFiles, String bundleLocation, String baseDirName,
			File tempDir)
		throws IOException {

		int index = bundleLocation.indexOf('@');

		if (index != -1) {
			bundleLocation = bundleLocation.substring(0, index);
		}

		if (!bundleLocation.startsWith("file:")) {
			bundleLocation = "file:" + baseDirName + bundleLocation;
		}

		if (!bundleLocation.endsWith(".jar")) {
			return;
		}

		URI uri = URI.create(bundleLocation);

		File bundleFile = new File(uri);

		if (!bundleFile.exists() || !bundleFile.canRead()) {
			return;
		}

		File jarFile = new File(tempDir, bundleFile.getName());

		Files.copy(
			bundleFile.toPath(), jarFile.toPath(),
			StandardCopyOption.COPY_ATTRIBUTES,
			StandardCopyOption.REPLACE_EXISTING);

		jarFiles.add(jarFile);
	}

	protected void processBundle(Bundle bundle) throws Exception {
		BundleRevision bundleRevision = bundle.adapt(BundleRevision.class);

		for (Capability capability : bundleRevision.getCapabilities(null)) {
			String namespace = capability.getNamespace();

			CapabilityBuilder capabilityBuilder = new CapabilityBuilder(
				namespace);

			capabilityBuilder.addAttributes(capability.getAttributes());
			capabilityBuilder.addDirectives(capability.getDirectives());

			Attrs attrs = capabilityBuilder.toAttrs();

			if (capabilityBuilder.isPackage()) {
				attrs.remove(Constants.BUNDLE_SYMBOLIC_NAME_ATTRIBUTE);
				attrs.remove(Constants.BUNDLE_VERSION_ATTRIBUTE);

				String packageName = attrs.remove(
					PackageNamespace.PACKAGE_NAMESPACE);

				if (packageName != null) {
					_packagesParamters.put(packageName, attrs);
				}
			}
			else if (!_ignoredNamespaces.contains(namespace)) {
				Parameters parameters = new Parameters();

				if (namespace.equals(NativeNamespace.NATIVE_NAMESPACE)) {
					Set<String> keys = new LinkedHashSet<>(attrs.keySet());

					for (String key : keys) {
						if (!key.startsWith(NativeNamespace.NATIVE_NAMESPACE)) {
							attrs.remove(key);
						}
					}
				}

				parameters.put(namespace, attrs);

				_parametersList.add(parameters);
			}
		}
	}

	private static final Set<String> _ignoredNamespaces = new HashSet<>();

	static {
		_ignoredNamespaces.add(BundleNamespace.BUNDLE_NAMESPACE);
		_ignoredNamespaces.add(ContentNamespace.CONTENT_NAMESPACE);
		_ignoredNamespaces.add(HostNamespace.HOST_NAMESPACE);
		_ignoredNamespaces.add(IdentityNamespace.IDENTITY_NAMESPACE);
		_ignoredNamespaces.add(PackageNamespace.PACKAGE_NAMESPACE);
	}

	private final String _bundleSymbolicName;
	private final String _bundleVersion;
	private final Map<String, String> _config = new HashMap<>();
	private final ModuleFrameworkImpl _moduleFrameworkImpl =
		new ModuleFrameworkImpl();
	private final Parameters _packagesParamters = new Parameters();
	private final List<Parameters> _parametersList = new ArrayList<>();

}