/*
 * This file is part of FTB Launcher.
 *
 * Copyright © 2012-2013, FTB Launcher Contributors <https://github.com/Slowpoke101/FTBLaunch/>
 * FTB Launcher is licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ftb.tools;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import net.ftb.data.ModPack;
import net.ftb.data.Settings;
import net.ftb.gui.LaunchFrame;
import net.ftb.gui.dialogs.ModpackUpdateDialog;
import net.ftb.log.Logger;
import net.ftb.util.DownloadUtils;
import net.ftb.util.ErrorUtils;
import net.ftb.util.FileUtils;
import net.ftb.util.OSUtils;
import net.ftb.util.TrackerUtils;

@SuppressWarnings("serial")
public class ModManager extends JDialog {
	public static boolean update = false, backup = false, erroneous = false, upToDate = false;
	private static String curVersion = "";
	private JPanel contentPane;
	private double downloadedPerc;
	private final JProgressBar progressBar;
	private final JLabel label;
	private static String sep = File.separator;
	public static ModManagerWorker worker;

	public class ModManagerWorker extends SwingWorker<Boolean, Void> {
		@Override
		protected Boolean doInBackground() {
			try {
				if(!upToDate()) {
					String installPath = OSUtils.getDynamicStorageLocation();
					ModPack pack = ModPack.getSelectedPack();
					pack.setUpdated(true);
					File modPackZip = new File(installPath, "ModPacks" + sep + pack.getDir() + sep + pack.getUrl());
					if(modPackZip.exists()) {
						FileUtils.delete(modPackZip);
					}
					File animationGif = new File(OSUtils.getDynamicStorageLocation(), "ModPacks" + sep + pack.getDir() + sep + pack.getAnimation());
					if(animationGif.exists()) {
						FileUtils.delete(animationGif);
					}
					erroneous = !downloadModPack(pack.getUrl(), pack.getDir());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}

		public String downloadUrl(String filename, String urlString) {
			BufferedInputStream in = null;
			FileOutputStream fout = null;
			HttpURLConnection connection = null;
			String md5 = "";
			try {
				URL url_ = new URL(urlString);
				byte data[] = new byte[1024];
				connection = (HttpURLConnection) url_.openConnection();
				connection.setAllowUserInteraction(true);
				connection.setConnectTimeout(14000);
				connection.setReadTimeout(20000);
				connection.connect();
				md5 = connection.getHeaderField("Content-MD5");
				in = new BufferedInputStream(connection.getInputStream());
				fout = new FileOutputStream(filename);
				int count, amount = 0, modPackSize = connection.getContentLength(), steps = 0;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						progressBar.setMaximum(10000);
					}
				});
				while((count = in.read(data, 0, 1024)) != -1) {
					fout.write(data, 0, count);
					downloadedPerc += (count * 1.0 / modPackSize) * 100;
					amount += count;
					steps++;
					if(steps > 100) {
						steps = 0;
						final String txt = (amount / 1024) + "Kb / " + (modPackSize / 1024) + "Kb";
						final int perc =  (int)downloadedPerc * 100;
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								progressBar.setValue(perc);
								label.setText(txt);
							}
						});
					}
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if(in != null) {
						in.close();
					}
					if(fout != null) {
						fout.flush();
						fout.close();
					}
					if(connection != null) {
						connection.disconnect();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return md5;
		}

		protected boolean downloadModPack(String modPackName, String dir) {
			boolean debugVerbose = Settings.getSettings().getDebugLauncher();
			String debugTag = "debug: downloadModPack: ";

			Logger.logInfo("Downloading Mod Pack");
			TrackerUtils.sendPageView("net/ftb/tools/ModManager.java", "Downloaded: " + modPackName + " v." + curVersion.replace('_', '.'));
			String dynamicLoc = OSUtils.getDynamicStorageLocation();
			String installPath = Settings.getSettings().getInstallPath();
			ModPack pack = ModPack.getSelectedPack();
			String baseLink = (pack.isPrivatePack() ? "privatepacks%5E" + dir + "%5E" + curVersion + "%5E" : "modpacks%5E" + dir + "%5E" + curVersion + "%5E");
			File baseDynamic = new File(dynamicLoc, "ModPacks" + sep + dir + sep);
			if (debugVerbose) {
				Logger.logInfo(debugTag + "pack dir: " + dir);
				Logger.logInfo(debugTag + "dynamicLoc: " + dynamicLoc);
				Logger.logInfo(debugTag + "installPath: " + installPath);
				Logger.logInfo(debugTag + "baseLink: " + baseLink);
			}
			baseDynamic.mkdirs();
			String md5 = "";
			try {
				new File(baseDynamic, modPackName).createNewFile();
				md5 = downloadUrl(baseDynamic.getPath() + sep + modPackName, DownloadUtils.getCreeperhostLink(baseLink + modPackName));
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			String animation = pack.getAnimation();
			if(!animation.equalsIgnoreCase("empty")) {
				try {
					downloadUrl(baseDynamic.getPath() + sep + animation, DownloadUtils.getCreeperhostLink(baseLink + animation));
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
			try {
				if((md5 == null || md5.isEmpty()) ? DownloadUtils.backupIsValid(new File(baseDynamic, modPackName), baseLink + modPackName) : DownloadUtils.isValid(new File(baseDynamic, modPackName), md5)) {
					if (debugVerbose) { Logger.logInfo(debugTag + "Extracting pack."); }
					FileUtils.extractZipTo(baseDynamic.getPath() + sep + modPackName, baseDynamic.getPath());
					if (debugVerbose) { Logger.logInfo(debugTag + "Purging mods, coremods, instMods"); }
					clearModsFolder(pack);
					FileUtils.delete(new File(installPath, dir + "/minecraft/coremods"));
					FileUtils.delete(new File(installPath, dir + "/instMods/"));
					File version = new File(installPath, dir + sep + "version");
					BufferedWriter out = new BufferedWriter(new FileWriter(version));
					out.write(curVersion.replace("_", "."));
					out.flush();
					out.close();
					if (debugVerbose) { Logger.logInfo(debugTag + "Pack extracted, version tagged."); }
					return true;
				} else {
					ErrorUtils.tossError("Error downloading modpack!!!");
					return false;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	/**
	 * Create the frame.
	 */
	public ModManager(JFrame owner, Boolean model) {
		super(owner, model);
		setResizable(false);
		setTitle("Downloading...");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setBounds(100, 100, 313, 138);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		progressBar = new JProgressBar();
		progressBar.setBounds(10, 63, 278, 22);
		contentPane.add(progressBar);

		JLabel lblDownloadingModPack = new JLabel("<html><body><center>Downloading mod pack...<br/>Please Wait</center></body></html>");
		lblDownloadingModPack.setHorizontalAlignment(SwingConstants.CENTER);
		lblDownloadingModPack.setBounds(0, 5, 313, 30);
		contentPane.add(lblDownloadingModPack);
		label = new JLabel("");
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBounds(0, 42, 313, 14);
		contentPane.add(label);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent arg0) {
				worker = new ModManagerWorker() {
					@Override
					protected void done() {
						setVisible(false);
						super.done();
					}
				};
				worker.execute();
			}
		});
	}

	private boolean upToDate() throws IOException {
		ModPack pack = ModPack.getSelectedPack();
		File version = new File(Settings.getSettings().getInstallPath(), pack.getDir() + sep + "version");
		if(!version.exists()) {
			version.getParentFile().mkdirs();
			version.createNewFile();
			curVersion = (Settings.getSettings().getPackVer().equalsIgnoreCase("recommended version") ? pack.getVersion() : Settings.getSettings().getPackVer()).replace(".", "_");
			return false;
		}
		BufferedReader in = new BufferedReader(new FileReader(version));
		String line = in.readLine();
		in.close();
		int currentVersion, requestedVersion;
		currentVersion = (line != null) ? Integer.parseInt(line.replace(".", "")) : 0;
		if(!Settings.getSettings().getPackVer().equalsIgnoreCase("recommended version") && !Settings.getSettings().getPackVer().equalsIgnoreCase("newest version")) {
			requestedVersion = Integer.parseInt(Settings.getSettings().getPackVer().trim().replace(".", ""));
			if(requestedVersion != currentVersion) {
				Logger.logInfo("Modpack is out of date.");
				curVersion = Settings.getSettings().getPackVer().replace(".", "_");
				return false;
			} else {
				Logger.logInfo("Modpack is up to date.");
				return true;
			}
		} else if(Integer.parseInt(pack.getVersion().replace(".", "")) > currentVersion) {
			Logger.logInfo("Modpack is out of date.");
			ModpackUpdateDialog p = new ModpackUpdateDialog(LaunchFrame.getInstance(), true);
			p.setVisible(true);
			if(!update) {
				return true;
			}
			if(backup) {
				File destination = new File(OSUtils.getDynamicStorageLocation(), "backups" + sep + pack.getDir() + sep + "config_backup");
				if(destination.exists()) {
					FileUtils.delete(destination);
				}
				FileUtils.copyFolder(new File(Settings.getSettings().getInstallPath(), pack.getDir() + sep + "minecraft" + sep + "config"), destination);
			}
			curVersion = pack.getVersion().replace(".", "_");
			return false;
		} else {
			Logger.logInfo("Modpack is up to date.");
			return true;
		}
	}

	public static void cleanUp() {
		ModPack pack = ModPack.getSelectedPack();
		File tempFolder = new File(OSUtils.getDynamicStorageLocation(), "ModPacks" + sep + pack.getDir() + sep);
		for(String file : tempFolder.list()) {
			if(!file.equals(pack.getLogoName()) && !file.equals(pack.getImageName()) && !file.equals("version") && !file.equals(pack.getAnimation())) {
				try {
					if (Settings.getSettings().getDebugLauncher() && file.endsWith(".zip")) {
						Logger.logInfo("debug: retaining modpack file: " + tempFolder + File.separator + file);
					} else {
						FileUtils.delete(new File(tempFolder, file));
					}
				} catch (IOException e) {
					Logger.logError(e.getMessage(), e);
				}
			}
		}
	}

	public static void clearModsFolder(ModPack pack) {
		File modsFolder = new File(Settings.getSettings().getInstallPath(), pack.getDir() + "/minecraft/mods");
		if(modsFolder.exists()) {
			for(String file : modsFolder.list()) {
				if(file.toLowerCase().endsWith(".zip") || file.toLowerCase().endsWith(".jar") || file.toLowerCase().endsWith(".disabled") || file.toLowerCase().endsWith(".litemod")) {
					try {
						FileUtils.delete(new File(modsFolder, file));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
