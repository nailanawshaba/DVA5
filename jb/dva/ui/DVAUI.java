//
//  MainWindow.java
//  DVA
//
//  Created by Jonathan Boles on 27/05/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//
package jb.dva.ui;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import jb.common.ExceptionReporter;
import jb.common.sound.LevelMeterPanel;
import jb.common.sound.Player;
import jb.common.ui.SimpleEditorUndoRedoKit;
import jb.common.ui.ToolTipList;
import jb.dva.Script;
import jb.dva.SoundInflection;
import jb.dva.SoundLibrary;
import jb.dva.SoundLibraryListCellRenderer;
import jb.dva.SoundListCellRenderer;
import jb.dva.SoundReference;
import jb.dvacommon.DVA;
import jb.dvacommon.Settings;
import jb.dvacommon.ui.DVATextArea;
import jb.dvacommon.ui.DVATextVerifyListener;
import org.jdesktop.swingx.JXBusyLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swixml.SwingEngine;

public class DVAUI {
    private JPanel panel;
    private DVA controller;
    private boolean documentModified = false;

    // Outlets
    public JScrollPane voicePane;
    public JList<SoundLibrary> voiceComboBox;
    public JList<Script> announcementComboBox;
    public JButton playStopCurrentAnnouncementButton;
    public JButton playStopSavedAnnouncementButton;
    public JLabel dvaTextAreaLabel;
    public DVATextArea dvaTextArea;
    public JLabel indicatorIconLabel;
    public JCheckBox previewSoundCheckbox;
    public JList<SoundReference> suggestedSoundList;
    public JPanel inflectionPanel;
    public JList<String> inflectionList;
    public JSplitPane splitpane;
    public JSplitPane splitpaneLeft;
    public JSplitPane splitpaneRight;
    public LevelMeterPanel levelMeterPanelCurrentAnnouncement;
    public LevelMeterPanel levelMeterPanelSavedAnnouncement;
    public JPanel soundInfoPanel;
    public JLabel soundInfoSizeLabel;
    public JLabel soundInfoDurationLabel;
    public JLabel soundInfoEncodingLabel;
    public JLabel soundInfoSampleRateLabel;
    public JLabel soundInfoBitsPerSampleLabel;
    public JLabel soundInfoChannelsLabel;
    public JLabel soundInfoBytesPerFrameLabel;
    public JLabel announcementStatsLabel;
    public JXBusyLabel playSavedAnnouncementBusyLabel;
    public JXBusyLabel playCurrentAnnouncementBusyLabel;

    public SoundListModel suggestedSoundListModel;
    public DefaultListModel<Script> announcementListModel;
    public DefaultListModel<String> inflectionListModel;
    
    public Script currentScript;

    final static Logger logger = LoggerFactory.getLogger(DVAUI.class);

    @SuppressWarnings("serial")
    public DVAUI(final DVA controller) {
        this.controller = controller;
        this.suggestedSoundListModel = new SoundListModel();
        SwingEngine renderer = new SwingEngine(this);
        renderer.getTaglib().registerTag("tooltiplist", ToolTipList.class);
        renderer.getTaglib().registerTag("levelmeterpanel", LevelMeterPanel.class);
        renderer.getTaglib().registerTag("xbusylabel", JXBusyLabel.class);
        renderer.getTaglib().registerTag("dvatextarea", DVATextArea.class);

        try {
            panel = (JPanel) renderer.render(DVAUI.class.getResource("/jb/dva/ui/resources/ui.xml"));
            currentScript = new Script("", "");
            
            DVATextVerifyListener verifyHandler = new DVATextVerifyListener() {
                public void OnFailed() {
                    
                }
                public void OnVerified() {
                    updateAnnouncementStats(controller.getVerifiedUrlList());
                }
            };
            dvaTextArea.initialize(controller, currentScript, indicatorIconLabel, verifyHandler, suggestedSoundList, suggestedSoundListModel);

            Collection<SoundLibrary> soundLibraryList = controller.getSoundLibraryList();
            voiceComboBox.setListData(soundLibraryList.toArray(new SoundLibrary[soundLibraryList.size()]));
            voiceComboBox.setSelectedIndex(0);
            voiceComboBox.setCellRenderer(new SoundLibraryListCellRenderer());
            
            suggestedSoundList.setCellRenderer(new SoundListCellRenderer());

            SoundLibrary selectedLibrary = voiceComboBox.getSelectedValue();
            currentScript.setVoice(selectedLibrary.getName());
            suggestedSoundListModel.setSoundLibrary(selectedLibrary);
            inflectionPanel.setVisible(selectedLibrary.supportsInflections());

            SimpleEditorUndoRedoKit.enableUndo(dvaTextArea);
            dvaTextArea.setFont(dvaTextAreaLabel.getFont());

            voiceComboBox.addListSelectionListener(e -> {
                SoundLibrary selectedLibrary1 = voiceComboBox.getSelectedValue();
                currentScript.setVoice(selectedLibrary1.getName());
                suggestedSoundListModel.setSoundLibrary(selectedLibrary1);
                inflectionPanel.setVisible(selectedLibrary1.supportsInflections());
                dvaTextArea.verify();
                suggestedSoundList.setSelectedIndex(0);
            });

            inflectionListModel = new DefaultListModel<>();
            inflectionListModel.addElement(" ");
            inflectionList.setModel(inflectionListModel);
            inflectionList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            inflectionList.setVisibleRowCount(-1);
            suggestedSoundList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int index = suggestedSoundList.getSelectedIndex();
                    if (index < 0 || index >= suggestedSoundList.getModel().getSize())
                    {
                        soundInfoDurationLabel.setText("");
                        soundInfoSizeLabel.setText("");
                        soundInfoEncodingLabel.setText("");
                        soundInfoSampleRateLabel.setText("");
                        soundInfoBitsPerSampleLabel.setText("");
                        soundInfoChannelsLabel.setText("");
                        soundInfoBytesPerFrameLabel.setText("");
                    }
                    else
                    {
                        SoundLibrary selectedLibrary1 = voiceComboBox.getSelectedValue();
                        SoundReference ref = suggestedSoundListModel.getElementAt(index);
                        if (selectedLibrary1.supportsInflections()) {
                            inflectionListModel.removeAllElements();
                            if (ref.regular != null) {
                                inflectionListModel.addElement(SoundInflection.getNameForInflection(SoundInflection.NONE));
                            }
                            if (ref.rising != null) {
                                inflectionListModel.addElement(SoundInflection.getNameForInflection(SoundInflection.RISING));
                            }
                            if (ref.falling != null) {
                                inflectionListModel.addElement(SoundInflection.getNameForInflection(SoundInflection.FALLING));
                            }
                            inflectionList.setSelectedIndex(-1);
                            inflectionList.setSelectedIndex(inflectionListModel.size() - 1);
                        } else {
                            showSoundInfo(index, SoundInflection.NONE);
                        }
                    }
                }
            });
            inflectionList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int index = inflectionList.getSelectedIndex();
                    int sindex = suggestedSoundList.getSelectedIndex();
                    if (sindex >= 0 && sindex < suggestedSoundList.getModel().getSize() && index >= 0 && index < inflectionList.getModel().getSize())
                    {
                        showSoundInfo(sindex, SoundInflection.getInflectionForName(inflectionList.getSelectedValue()));
                    }
                }
            });
            MouseAdapter clickHandler = new MouseAdapter() {
                public void mouseClicked(MouseEvent e)
                {
                    int sindex = suggestedSoundList.getSelectedIndex();
                    if (sindex < suggestedSoundListModel.getSize())
                    {
                        if (e.getClickCount() == 2)
                        {
                            currentScript.setScript(dvaTextArea.getText());
                            dvaTextArea.autoComplete();
                        }
                        else if (e.getClickCount() == 1 && previewSoundCheckbox.isSelected())
                        {
                            SoundLibrary selectedLibrary = voiceComboBox.getSelectedValue();
                            if (selectedLibrary.supportsInflections()) {
                                previewSound(sindex, SoundInflection.getInflectionForName(inflectionList.getSelectedValue()));
                            } else {
                                previewSound(sindex, SoundInflection.NONE);
                            }
                        }
                    }
                }
            };
            suggestedSoundList.addMouseListener(clickHandler);
            inflectionList.addMouseListener(clickHandler);
            suggestedSoundList.addMouseMotionListener(new MouseAdapter() {
                public void mouseDragged(MouseEvent e)
                {
                    TransferHandler th = suggestedSoundList.getTransferHandler();
                    th.exportAsDrag(suggestedSoundList, e, TransferHandler.COPY);
                }
            });
            suggestedSoundList.setTransferHandler(new TransferHandler() {
                protected Transferable createTransferable(JComponent c)
                {
                    @SuppressWarnings("unchecked")
                    JList<SoundReference> source = (JList<SoundReference>)c;
                    if (source.getSelectedIndex() > 0 && source.getSelectedIndex() < source.getModel().getSize())
                    {
                        String s = source.getSelectedValue().toString();
                        return new StringSelection(s);
                    }

                    return null;
                }

                public int getSourceActions( JComponent c ) {
                    return COPY;
                }
            });

            announcementListModel = new DefaultListModel<>();
            for (Script s : Settings.loadAnnouncements(controller.getSoundLibraryNames()))
            {
                announcementListModel.addElement(s);
            }
            announcementListModel.addListDataListener(new ListDataListener() {
                public void contentsChanged(ListDataEvent e) {
                    save();
                }
                public void intervalAdded(ListDataEvent e) {
                    save();
                }
                public void intervalRemoved(ListDataEvent e) {
                    save();
                }
                public void save() {
                    Settings.saveAnnouncements(new AbstractList<Script>() {
                        public Script get(int index) {
                            return announcementListModel.elementAt(index);
                        }

                        public int size() {
                            return announcementListModel.size();
                        }
                    });
                }
            });
            announcementComboBox.setModel(announcementListModel);
            //            announcementComboBox.setCellRenderer(new AnnouncementListCellRenderer(controller));

            announcementComboBox.addListSelectionListener(e -> {
                moveUpAction.setEnabled(announcementComboBox.getSelectedIndex() > 0);
                moveDownAction.setEnabled(announcementComboBox.getSelectedIndex() >= 0 && announcementComboBox.getSelectedIndex() < announcementListModel.size() - 1);
                renameAction.setEnabled(announcementComboBox.getSelectedIndex() >= 0);
                deleteAction.setEnabled(announcementComboBox.getSelectedIndex() >= 0);
                openAction.setEnabled(announcementComboBox.getSelectedIndex() >= 0);
                playSavedAction.setEnabled(announcementComboBox.getSelectedIndex() >= 0);
            });

            moveUpAction.setEnabled(false);
            moveDownAction.setEnabled(false);
            renameAction.setEnabled(false);
            deleteAction.setEnabled(false);
            openAction.setEnabled(false);
            playSavedAction.setEnabled(false);
            stopAction.setEnabled(false);

            suggestedSoundList.setModel(suggestedSoundListModel);

            splitpane.setDividerLocation(0.4);
            splitpaneLeft.setDividerLocation(0.4);
            splitpaneRight.setDividerLocation(0.4);

            flattenJSplitPane(splitpane);
            flattenJSplitPane(splitpaneLeft);
            flattenJSplitPane(splitpaneRight);
        } catch (Exception e) {
            ExceptionReporter.reportException(e);
        }
    }
    
    public JPanel getPanel() {
        return this.panel;
    }

    public void previewSound(int index, int inflection)
    {
        new Player(Collections.singletonList(suggestedSoundListModel.getURLAt(index, inflection)), levelMeterPanelCurrentAnnouncement).start();
    }

    public void showSoundInfo(int index, int inflection)
    {
        try {
            logger.debug("showSoundInfo index {} inflection {}", index, inflection);
            URL u = suggestedSoundListModel.getURLAt(index, inflection);
            if (u == null)
            {
                return;
            }
            InputStream is = u.openStream();
            long size = is.available();
            AudioInputStream ais = AudioSystem.getAudioInputStream(u);
            AudioFormat format = ais.getFormat();
            final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
            int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
            String sizeString = new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];

            float duration = getDuration(u);
            String durationString = new DecimalFormat("0.##").format(duration) + " s";
            soundInfoDurationLabel.setText(durationString);
            soundInfoSizeLabel.setText(sizeString);
            soundInfoEncodingLabel.setText(format.getEncoding().toString());
            soundInfoSampleRateLabel.setText(format.getSampleRate() + " Hz");
            soundInfoBitsPerSampleLabel.setText(format.getSampleSizeInBits() >=0 ? format.getSampleSizeInBits() + "-bit" : "-");
            soundInfoChannelsLabel.setText(format.getChannels() == 2 ? "stereo" : "mono");
            soundInfoBytesPerFrameLabel.setText(format.getFrameSize() >= 0? format.getFrameSize() + " bytes" : "-");
        }
        catch (Exception ex) {
            ExceptionReporter.reportException(ex);
        }
    }

    @SuppressWarnings("serial")
    public Action voiceLibraryToggleAction = new AbstractAction("Show/Hide Voice Library List", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/History16.gif"))) {
        public void actionPerformed(ActionEvent e) {
            boolean visible = voicePane.isVisible();
            voicePane.setVisible(!visible);
            splitpaneLeft.resetToPreferredSizes();
        }
    };

    @SuppressWarnings("serial")
    public Action soundInfoAction = new AbstractAction("Show/Hide Sound Info", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/Information16.gif"))) {
        public void actionPerformed(ActionEvent e) {
            soundInfoPanel.setVisible(!soundInfoPanel.isVisible());
        }
    };

    @SuppressWarnings("serial")
    public Action playSavedAction = new AbstractAction("Play", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/media/Volume24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            play(this, true);
        }
    };

    @SuppressWarnings("serial")
    public Action playCurrentAction = new AbstractAction("Play", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/media/Volume24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            play(this, false);
        }
    };

    private void play(final Action action, final boolean playSaved)
    {
        final JButton playStopButton = playSaved? playStopSavedAnnouncementButton : playStopCurrentAnnouncementButton;
        LevelMeterPanel levelMeterPanel;
        Script script;
        if (playSaved) {
            // Playing saved announcement. Don't verify.
            script = announcementComboBox.getSelectedValue();
            levelMeterPanel = levelMeterPanelSavedAnnouncement;
        } else {
            script = currentScript;
            levelMeterPanel = levelMeterPanelCurrentAnnouncement;

            // Playing current announcement - verify.
            if (dvaTextArea.verify() >= 0) {
                // there was a parse error
                failSound();
                return;
            }
            dvaTextArea.canonicalise();
        }

        if (controller.getSoundLibrary(script.getVoice()) != null)
        {
            playSavedAction.setEnabled(false);
            playCurrentAction.setEnabled(false);
            stopAction.setEnabled(true);
            playStopButton.setAction(stopAction);

            final Runnable longConcatCallback = () -> SwingUtilities.invokeLater(() -> {
                if (playSaved) {
                    playSavedAnnouncementBusyLabel.setBusy(true);
                } else {
                    playCurrentAnnouncementBusyLabel.setBusy(true);
                }
            });

            final Runnable afterConcatCallback = () -> SwingUtilities.invokeLater(() -> {
                playSavedAnnouncementBusyLabel.setBusy(false);
                playCurrentAnnouncementBusyLabel.setBusy(false);
            });

            final Player p = controller.play(levelMeterPanel, script, longConcatCallback, afterConcatCallback);
            new Thread() {
                public void run() {
                    try {
                        p.join();

                        stopAction.setEnabled(false);
                        playSavedAction.setEnabled(true);
                        playCurrentAction.setEnabled(true);
                        afterConcatCallback.run();
                        playStopButton.setAction(action);
                    } catch (InterruptedException ignored) {
                    }
                }
            }.start();
            p.start();
        } else {
            JOptionPane.showMessageDialog(panel, "Sound library named '" + script.getVoice() + "' was not found.");
        }
    }

    @SuppressWarnings("serial")
    public Action stopAction = new AbstractAction("Stop", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/media/Stop24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            stopAction.setEnabled(false);
            playCurrentAction.setEnabled(true);
            playSavedAction.setEnabled(true);
            playStopCurrentAnnouncementButton.setAction(playCurrentAction);
            playStopSavedAnnouncementButton.setAction(playSavedAction);
            controller.stop();
        }
    };

    @SuppressWarnings("serial")
    public Action newAction = new AbstractAction("New", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/New24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            if (documentModified && abortDestructiveAction(e)) return;

            dvaTextArea.setText("");
            currentScript.setScript("");
            currentScript.setVoice((voiceComboBox.getSelectedValue()).getName());
            documentModified = false;
        }
    };

    @SuppressWarnings("serial")
    public Action openAction = new AbstractAction("Load", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/Open24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            if (documentModified && abortDestructiveAction(e)) return;

            Script loadScript = announcementComboBox.getSelectedValue();
            currentScript.setVoice(loadScript.getVoice());
            currentScript.setScript(loadScript.getScript());
            voiceComboBox.setSelectedValue(controller.getSoundLibrary(loadScript.getVoice()), true);
            dvaTextArea.setText(loadScript.getScript());
            documentModified = false;
        }
    };

    @SuppressWarnings("serial")
    public Action moveUpAction = new AbstractAction("Up", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/navigation/Up24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            int index = announcementComboBox.getSelectedIndex();
            if (index > 0)
            {
                Script s = announcementListModel.remove(index);
                announcementListModel.insertElementAt(s, index - 1);
                announcementComboBox.setSelectedValue(s, true);
            }
        }
    };

    @SuppressWarnings("serial")
    public Action moveDownAction = new AbstractAction("Down", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/navigation/Down24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            int index = announcementComboBox.getSelectedIndex();
            if (index < announcementListModel.size() - 1)
            {
                Script s = announcementListModel.remove(index);
                announcementListModel.insertElementAt(s, index + 1);
                announcementComboBox.setSelectedValue(s, true);
            }
        }
    };

    @SuppressWarnings("serial")
    public Action renameAction = new AbstractAction("Rename", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/SaveAs24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            String name = JOptionPane.showInputDialog("Choose a name for this announcement");
            if (name == null || name.trim().isEmpty()) return;

            for (int i = 0; i < announcementListModel.size(); i++)
            {
                if ((announcementListModel.getElementAt(i)).getName().toLowerCase().equals(name.toLowerCase()))
                {
                    if (confirmOverwrite())
                    {
                        announcementListModel.removeElement(announcementListModel.getElementAt(i));
                    }
                    else
                    {
                        return;
                    }
                    break;
                }
            }
            int index = announcementComboBox.getSelectedIndex();
            Script s = announcementListModel.remove(index);
            s.setName(name);
            announcementListModel.insertElementAt(s, index);
        }
    };

    @SuppressWarnings("serial")
    public Action deleteAction = new AbstractAction("Delete", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/Delete24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            Object ann = announcementComboBox.getSelectedValue();
            if (announcementComboBox.getSelectedValue() != null)
            {
                if (!confirmDelete()) return;
                announcementListModel.removeElement(ann);
            }
        }
    };

    @SuppressWarnings("serial")
    public Action exportAction = new AbstractAction("Export", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/SaveAs24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            int errorPos = controller.verify(currentScript);
            if (errorPos >= 0) {
                // there was a parse error
                failSound();
            } else {
                JFileChooser saveDialog = new JFileChooser();
                FileNameExtensionFilter wavFilter = new FileNameExtensionFilter("WAV Sound Files", "wav");
                FileNameExtensionFilter mp3Filter = new FileNameExtensionFilter("MP3 Sound Files", "mp3");
                saveDialog.addChoosableFileFilter(mp3Filter);
                saveDialog.addChoosableFileFilter(wavFilter);
                saveDialog.setFileFilter(mp3Filter);
                int retval = saveDialog.showSaveDialog(panel);
                if (retval == JFileChooser.APPROVE_OPTION)
                {
                    String path = saveDialog.getSelectedFile().getPath();
                    if (saveDialog.getFileFilter() == wavFilter && !path.toLowerCase().endsWith(".wav"))
                    {
                        path = path + ".wav";
                    } else if (saveDialog.getFileFilter() == mp3Filter && !path.toLowerCase().endsWith(".mp3"))
                    {
                        path = path + ".mp3";
                    }

                    try {
                        controller.export(currentScript, path);
                        if (SwingEngine.isMacOSX()) {
                            new ProcessBuilder("open", "-R", path).start();
                        } else {
                            new ProcessBuilder("explorer.exe", "/select,", path).start();
                        }
                    } catch (Exception ex) {
                        ExceptionReporter.reportException(ex);
                    }
                }
            }
        }
    };

    @SuppressWarnings("serial")
    public Action saveAction = new AbstractAction("Save", new ImageIcon(DVAUI.class.getResource("/toolbarButtonGraphics/general/Save24.gif"))) {
        public void actionPerformed(ActionEvent e) {
            String name = JOptionPane.showInputDialog("Choose a name for this announcement");
            if (name == null || name.trim().isEmpty()) return;

            for (int i = 0; i < announcementListModel.size(); i++)
            {
                if ((announcementListModel.getElementAt(i)).getName().toLowerCase().equals(name.toLowerCase()))
                {
                    if (confirmOverwrite())
                    {
                        announcementListModel.removeElement(announcementListModel.getElementAt(i));
                    }
                    else
                    {
                        return;
                    }
                    break;
                }
            }

            Script s = new Script(name, currentScript.getVoice(), currentScript.getScript());
            announcementListModel.addElement(s);
            documentModified = false;
        }
    };

    public void failSound()
    {
        new Player(Collections.singletonList(DVAUI.class.getResource("/resources/Basso.wav")), null).start();
    }

    private boolean abortDestructiveAction(ActionEvent e) {
        int retval = JOptionPane.showConfirmDialog(panel, "Announcement has changed. Save changes?", "", JOptionPane.YES_NO_CANCEL_OPTION);
        if (retval == JOptionPane.YES_OPTION) {
            saveAction.actionPerformed(e);
            return false;
        } else if (retval == JOptionPane.NO_OPTION) {
            return false;
        } else {
            return true;
        }
    }

    private boolean confirmDelete() {
        int retval = JOptionPane.showConfirmDialog(panel, "Are you sure you want to delete this announcement?", "", JOptionPane.YES_NO_OPTION);
        return (retval == JOptionPane.YES_OPTION);
    }

    private boolean confirmOverwrite() {
        int retval = JOptionPane.showConfirmDialog(panel, "An announcement with this name already exists. Overwrite?", "", JOptionPane.YES_NO_OPTION);
        return (retval == JOptionPane.YES_OPTION);
    }

    private static final ScheduledExecutorService updateAnnouncementStatsWorker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> updateAnnouncementStatsTask;
    private void updateAnnouncementStats(final ArrayList<URL> al) {
        if (updateAnnouncementStatsTask != null) {
            updateAnnouncementStatsTask.cancel(true);
        }
        updateAnnouncementStatsTask = updateAnnouncementStatsWorker.schedule((Runnable) () -> {
            int soundCount = 0;
            float duration = 0;
            for (Object o : al) {
                if (o instanceof String) {
                    duration += 0.4;
                } else if (o instanceof URL) {
                    soundCount++;
                    try {
                        duration += getDuration((URL) o);
                    } catch (Exception e) {
                        ExceptionReporter.reportException(e);
                    }
                }
            }

            String durationString = new DecimalFormat("0.##").format(duration) + " s";
            announcementStatsLabel.setText(Integer.toString(soundCount) + " sounds (" + durationString + ")");
        }, 300, TimeUnit.MILLISECONDS);
    }

    public static void flattenJSplitPane(JSplitPane splitPane) {
        splitPane.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        BasicSplitPaneUI flatDividerSplitPaneUI = new BasicSplitPaneUI() {
            @SuppressWarnings("serial")
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    public void setBorder(Border b) {
                    }
                };
            }
        };
        splitPane.setUI(flatDividerSplitPaneUI);
        splitPane.setBorder(null);
    }

    private static float getDuration(URL u) {
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(u);
            AudioFormat baseFormat = in.getFormat();
            AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);

            BufferedInputStream bin = new BufferedInputStream(din);
            bin.mark(Integer.MAX_VALUE);
            int size = 0;
            byte[] buf = new byte[4096];
            int bytesRead;
            while ((bytesRead = bin.read(buf, 0, buf.length)) >= 0) {
                size += bytesRead;
            }
            bin.reset();

            float duration = size / decodedFormat.getFrameSize() / decodedFormat.getFrameRate();
            in.close();
            return duration;
        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
