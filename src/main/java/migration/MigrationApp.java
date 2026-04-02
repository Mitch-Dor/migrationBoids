package migration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class MigrationApp extends JFrame {

    private final MigrationSim   sim   = new MigrationSim();
    private final MigrationPanel panel = new MigrationPanel(sim);

    private long lastTime = System.nanoTime();

    public MigrationApp() {
        super("Animal Migration Simulation");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
        setResizable(true);

        // Game loop — target ~60fps, pass real elapsed time as dt
        Timer timer = new Timer(16, (ActionEvent e) -> {
            long now = System.nanoTime();
            double dt = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;
            dt = Math.min(dt, 0.05); // cap at 50ms to avoid spiral of death on lag
            sim.step(dt);
            panel.repaint();
        });
        timer.start();
    }

    private JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY));

        JButton resetBtn = new JButton("Reset simulation");
        resetBtn.setFocusPainted(false);
        resetBtn.addActionListener(e -> sim.restart());
        p.add(resetBtn);

        JButton switchBtn = new JButton("Force node switch");
        switchBtn.setFocusPainted(false);
        switchBtn.addActionListener(e -> sim.forceSwitch());
        p.add(switchBtn);

        p.add(new JLabel("Speed:"));
        JSlider speedSlider = new JSlider(1, 5, 2);
        speedSlider.setPreferredSize(new Dimension(100, 24));
        JLabel speedLabel = new JLabel("2");
        speedLabel.setPreferredSize(new Dimension(20, 20));
        speedSlider.addChangeListener(ce -> {
            int v = speedSlider.getValue();
            speedLabel.setText(String.valueOf(v));
            sim.setSpeedMult(v / 2.0);
        });
        p.add(speedSlider);
        p.add(speedLabel);

        return p;
    }
}
