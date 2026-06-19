package com.paulzzh.checkupdate.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

import static com.paulzzh.checkupdate.Main.info;

public class MainWindow extends JFrame {
    public static MainWindow INSTANCE;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel root = new JPanel(cardLayout);
    private final JPanel loadingPanel = new JPanel(new GridBagLayout());
    private final JPanel mainPanel = new JPanel(new BorderLayout());

    private BootstrapLikeRowPanel head;
    private DownloadTaskPanel foot;
    private LogPanel logPanel;

    public MainWindow() {
        INSTANCE = this;
        setTitle("少女祈祷中...");
        setIconImage(new ImageIcon(MainWindow.class.getResource("/assets/checkupdate/icon.png")).getImage());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        setSize(800, 450);
        setMinimumSize(new Dimension(800, 450));

        ImageBackgroundPanel background = new ImageBackgroundPanel(new ImageIcon("CheckUpdateCache/info/background.png").getImage());
        background.setLayout(new BorderLayout());

        buildLoadingPanel();

        root.setOpaque(false);
        loadingPanel.setOpaque(false);
        mainPanel.setOpaque(false);

        root.add(loadingPanel, "少女祈祷中...");
        root.add(mainPanel, "少女祈祷中...");

        background.add(root, BorderLayout.CENTER);
        setContentPane(background);

        setLocationRelativeTo(null);
        setVisible(true);

        cardLayout.show(root, "少女祈祷中...");
    }

    private static JPanel wrap(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void buildLoadingPanel() {
        JLabel label = new JLabel("少女祈祷中...");
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        loadingPanel.add(label);
    }

    public void showMainUI(ImageIcon icon, String name, String version, Image backgroundImage) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            setTitle(name + " -- 检查更新");

            if (getContentPane() instanceof ImageBackgroundPanel) {
                ImageBackgroundPanel bgPanel = (ImageBackgroundPanel) getContentPane();
                bgPanel.setBackgroundImage(backgroundImage);
                bgPanel.repaint();
            }

            head = new BootstrapLikeRowPanel(icon, name, version);
            head.setBorder(new EmptyBorder(0, 20, 0, 20));

            DownloadTaskPanel taskPanel = new DownloadTaskPanel();
            taskPanel.setBorder(new EmptyBorder(0, 20, 20, 5));

            logPanel = new LogPanel();
            logPanel.setBorder(new EmptyBorder(0, 5, 20, 20));

            JSplitPane footSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wrap(taskPanel), wrap(logPanel));
            footSplit.setBorder(null);
            footSplit.setOpaque(false);
            footSplit.setContinuousLayout(true);
            footSplit.setResizeWeight(0.5);   // 左右各一半
            footSplit.setDividerSize(0);

            foot = taskPanel; // 如果你还想保留原来的 foot 字段，就让它指向左侧表格

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, wrap(head), wrap(footSplit));
            split.setBorder(null);
            split.setOpaque(false);
            split.setContinuousLayout(true);
            split.setResizeWeight(0.35);
            split.setDividerSize(0);

            mainPanel.removeAll();
            mainPanel.add(split, BorderLayout.CENTER);
            mainPanel.revalidate();
            mainPanel.repaint();

            cardLayout.show(root, "main");
            SwingUtilities.invokeLater(() -> {
                split.setDividerLocation(0.35);
                footSplit.setDividerLocation(0.5);
            });

            info("UI 加载完毕");
        });
    }

    public BootstrapLikeRowPanel getHead() {
        return head;
    }

    public DownloadTaskPanel getFoot() {
        return foot;
    }

    public LogPanel getLog() {
        return logPanel;
    }
}