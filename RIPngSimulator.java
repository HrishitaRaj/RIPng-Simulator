import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class Router {
    int id;
    String ipv6;
    int[] hopCount;
    int[] cost;

    public Router(int id, int n, String ipv6) {
        this.id = id;
        this.ipv6 = ipv6;

        hopCount = new int[n];
        cost = new int[n];

        Arrays.fill(hopCount, 16); // RIPng infinity = 16
        Arrays.fill(cost, 16);

        hopCount[id] = 0;
    }

    void setLink(int neighbor, int c) {
        cost[neighbor] = c;
        hopCount[neighbor] = c;
    }

    boolean update(int[][] tables) {
        boolean updated = false;

        for (int d = 0; d < hopCount.length; d++) {
            for (int n = 0; n < cost.length; n++) {

                if (cost[n] < 16 && tables[n][d] < 16) {

                    int newHop = cost[n] + tables[n][d];

                    if (newHop > 16) newHop = 16;

                    if (newHop < hopCount[d]) {
                        hopCount[d] = newHop;
                        updated = true;
                    }
                }
            }
        }
        return updated;
    }
}

class Packet {
    int from, to;
    double progress = 0;
    String label;

    public Packet(int from, int to, String label) {
        this.from = from;
        this.to = to;
        this.label = label;
    }
}

class NetworkPanel extends JPanel {
    int[][] pos = {
        {100, 250}, {300, 100}, {500, 250}, {300, 400}
    };

    boolean linkFailed = false;
    boolean[] updatedRouters;
    java.util.List<Packet> packets = new ArrayList<>();

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawEdge(g2, 0, 1, 1);
        drawEdge(g2, 0, 2, 4);
        drawEdge(g2, 1, 2, 2);
        drawEdge(g2, 1, 3, 6);
        drawEdge(g2, 2, 3, 3);

        for (int i = 0; i < pos.length; i++) {
            int x = pos[i][0];
            int y = pos[i][1];

            if (updatedRouters != null && updatedRouters[i]) {
                g2.setColor(Color.YELLOW);
            } else {
                g2.setColor(new Color(0, 200, 200));
            }

            g2.fillOval(x, y, 50, 50);
            g2.setColor(Color.BLACK);
            g2.drawOval(x, y, 50, 50);
            g2.drawString("R" + i, x + 15, y + 30);
        }

        for (Packet p : packets) {

    int x1 = pos[p.from][0] + 25;
    int y1 = pos[p.from][1] + 25;
    int x2 = pos[p.to][0] + 25;
    int y2 = pos[p.to][1] + 25;

    int x = (int)(x1 + (x2 - x1) * p.progress);
    int y = (int)(y1 + (y2 - y1) * p.progress);

    // glow
    g.setColor(new Color(0, 102, 255, 150));
    g.fillOval(x - 12, y - 12, 28, 28);

    // main packet
    g.setColor(Color.BLUE);
    g.fillOval(x - 6, y - 6, 16, 16);

    // label
    g.setColor(Color.BLACK);
    g.drawString("Upd", x + 5, y - 5);
}
    }

    void drawEdge(Graphics2D g, int a, int b, int cost) {
        int x1 = pos[a][0] + 25;
        int y1 = pos[a][1] + 25;
        int x2 = pos[b][0] + 25;
        int y2 = pos[b][1] + 25;

        if ((a == 1 && b == 2 || a == 2 && b == 1) && linkFailed) {
            g.setColor(Color.RED);
        } else {
            g.setColor(Color.BLACK);
        }

        g.drawLine(x1, y1, x2, y2);

        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;

        int dx = x2 - x1;
        int dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);

        int textX = (int)(midX - dy / len * 15);
        int textY = (int)(midY + dx / len * 15);

        g.setColor(Color.WHITE);
        g.fillRect(textX - 10, textY - 15, 25, 20);

        g.setColor(Color.BLUE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString(String.valueOf(cost), textX, textY);
    }
}

public class RIPngSimulator extends JFrame {

    Router[] routers;
    int n = 4;

    NetworkPanel panel;
    JTextArea log;
    boolean[] updatedRouters;

    int[][] prevTables;

    int selectedRouter = -1; // -1 = show all

    boolean paused = false;
    final Object lock = new Object();

    JTable beforeTable, afterTable;
    String[] columns = {"Router", "R0", "R1", "R2", "R3"};

    public RIPngSimulator() {
        setTitle("RIPng (IPv6 Distance Vector) Simulator");
        setSize(950, 600);
        setLayout(new BorderLayout());

        panel = new NetworkPanel();
        log = new JTextArea();
        prevTables = new int[n][n];
        log.setEditable(false);

        beforeTable = new JTable(new Object[n][n+1], columns);
        afterTable = new JTable(new Object[n][n+1], columns);

        JPanel tablePanel = new JPanel(new GridLayout(1, 2));

// BEFORE PANEL
JPanel beforePanel = new JPanel(new BorderLayout());
JLabel beforeLabel = new JLabel("Before Routing Table", JLabel.CENTER);
beforeLabel.setFont(new Font("Arial", Font.BOLD, 14));
beforePanel.add(beforeLabel, BorderLayout.NORTH);
beforePanel.add(new JScrollPane(beforeTable), BorderLayout.CENTER);

// AFTER PANEL
JPanel afterPanel = new JPanel(new BorderLayout());
JLabel afterLabel = new JLabel("After Routing Table", JLabel.CENTER);
afterLabel.setFont(new Font("Arial", Font.BOLD, 14));
afterPanel.add(afterLabel, BorderLayout.NORTH);
afterPanel.add(new JScrollPane(afterTable), BorderLayout.CENTER);

// ADD TO MAIN PANEL
tablePanel.add(beforePanel);
tablePanel.add(afterPanel);
        

        log.append("\nLegend:\n");
        log.append("Yellow = Updating Router\n");
        log.append("Blue Packet = RIPng Update\n\n");

        JPanel controls = new JPanel();

        JButton run = new JButton("Run RIPng");
JButton pauseBtn = new JButton("Pause");
JButton stepBtn = new JButton("Next Step");
JButton fail = new JButton("Fail Link");
JButton reset = new JButton("Reset");

        controls.add(run);
controls.add(pauseBtn);
controls.add(stepBtn);
controls.add(fail);
controls.add(reset);

    
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

// TOP = graph
verticalSplit.setTopComponent(panel);

// BOTTOM = tables
verticalSplit.setBottomComponent(tablePanel);

verticalSplit.setDividerLocation(350);  // adjust if needed

JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

// LEFT = graph + tables
mainSplit.setLeftComponent(verticalSplit);

// RIGHT = logs
mainSplit.setRightComponent(new JScrollPane(log));

mainSplit.setDividerLocation(650);

add(mainSplit, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        run.addActionListener(e -> runSimulation());
        fail.addActionListener(e -> failLink());
        reset.addActionListener(e -> reset());

        pauseBtn.addActionListener(e -> {
    paused = !paused;
    pauseBtn.setText(paused ? "Resume" : "Pause");
});

stepBtn.addActionListener(e -> {
    synchronized(lock) {
        paused = false;
        lock.notify();
    }
});

        init();

        panel.addMouseListener(new MouseAdapter() {
            @Override
    public void mouseClicked(MouseEvent e) {

        int x = e.getX();
        int y = e.getY();

        int[][] pos = panel.pos;

        for (int i = 0; i < pos.length; i++) {
            int rx = pos[i][0];
            int ry = pos[i][1];

            // check if click inside circle
            if (Math.hypot(x - (rx + 25), y - (ry + 25)) <= 25) {

                if (selectedRouter == i)
                    selectedRouter = -1; // toggle off
                else
                    selectedRouter = i;

                updateTablesUI();
                break;
            }
        }
    }
});

        TableCellRendererCustom renderer = new TableCellRendererCustom(prevTables, routers);

        for (int i = 0; i < afterTable.getColumnCount(); i++) {
            afterTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    void init() {
        routers = new Router[n];
        updatedRouters = new boolean[n];

        prevTables = new int[n][n];

        routers[0] = new Router(0, n, "2001:db8::1");
        routers[1] = new Router(1, n, "2001:db8::2");
        routers[2] = new Router(2, n, "2001:db8::3");
        routers[3] = new Router(3, n, "2001:db8::4");

        routers[0].setLink(1, 1);
        routers[0].setLink(2, 4);

        routers[1].setLink(0, 1);
        routers[1].setLink(2, 2);
        routers[1].setLink(3, 6);

        routers[2].setLink(0, 4);
        routers[2].setLink(1, 2);
        routers[2].setLink(3, 3);

        routers[3].setLink(1, 6);
        routers[3].setLink(2, 3);

        log.append("Protocol: RIPng (IPv6 Distance Vector)\n");
    }


    void runSimulation() {
        new Thread(() -> {
            boolean changed;
            int step = 0;

            do {
                synchronized(lock) {
    while (paused) {
        try {
            lock.wait();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}
                changed = false;
                step++;

                int[][] tables = new int[n][n];

                for (int i = 0; i < n; i++) {
                    tables[i] = routers[i].hopCount.clone();
                    updatedRouters[i] = false;
                }

                log.append("\n--- RIPng Update Step " + step + " ---\n");

                panel.updatedRouters = updatedRouters;
                panel.repaint();

                for (int i = 0; i < n; i++) {
                    prevTables[i] = routers[i].hopCount.clone();
                }

                for (int i = 0; i < n; i++) {
                    Arrays.fill(updatedRouters, false);
                    if (routers[i].update(tables)) {
                        updatedRouters[i] = true;
                        changed = true;

                    log.append("RIPng Update from " + routers[i].ipv6 + "\n");

                    // 🔥 TRIGGER animation HERE
                    animatePackets(i);

        try { Thread.sleep(900); } catch (Exception ignored) {}
    }
}
                
                updateTablesUI();

                try { Thread.sleep(1000); } catch (Exception ignored) {}

            } while (changed);

            log.append("\nRIPng Converged\n");

        }).start();
    }

    void animatePackets(int sender) {

    // create packets to neighbors
    for (int j = 0; j < n; j++) {
        if (routers[sender].cost[j] < 16 && sender != j) {
            Packet p = new Packet(sender, j, "Upd");
            p.progress = 0;
            panel.packets.add(p);
        }
    }

    // Use Swing Timer (IMPORTANT for UI)
    new javax.swing.Timer(40, new ActionListener() {

        int step = 0;

        public void actionPerformed(ActionEvent e) {

            step++;

            for (Packet p : panel.packets) {
                p.progress += 0.02;
            }

            panel.repaint();

            if (step > 50) {
                ((javax.swing.Timer)e.getSource()).stop();
                panel.packets.removeIf(p -> p.progress >= 1.0);
                panel.repaint();
            }
        }

    }).start();
}

    void failLink() {
        log.append("\nLink between R1 and R2 failed\n");

        routers[1].cost[2] = 16;
        routers[2].cost[1] = 16;

        for (int i = 0; i < n; i++) {
            Arrays.fill(routers[i].hopCount, 16);
            routers[i].hopCount[i] = 0;

            for (int j = 0; j < n; j++) {
                if (routers[i].cost[j] < 16) {
                    routers[i].hopCount[j] = routers[i].cost[j];
                }
            }
        }

        panel.linkFailed = true;
        panel.repaint();
    }

    void updateTablesUI() {

    for (int i = 0; i < n; i++) {

        if (selectedRouter != -1 && i != selectedRouter) {
            // hide other rows
            for (int j = 0; j <= n; j++) {
                beforeTable.setValueAt("", i, j);
                afterTable.setValueAt("", i, j);
            }
            continue;
        }

        beforeTable.setValueAt("R" + i, i, 0);
        afterTable.setValueAt("R" + i, i, 0);

        for (int j = 0; j < n; j++) {

            String before = prevTables[i][j] == 16 ? "INF" : String.valueOf(prevTables[i][j]);
            String after  = routers[i].hopCount[j] == 16 ? "INF" : String.valueOf(routers[i].hopCount[j]);

            beforeTable.setValueAt(before, i, j + 1);
            afterTable.setValueAt(after, i, j + 1);
        }
    }
}

    class TableCellRendererCustom extends DefaultTableCellRenderer {

    int[][] prev;
    Router[] routers;

    public TableCellRendererCustom(int[][] prev, Router[] routers) {
        this.prev = prev;
        this.routers = routers;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int col) {

        Component c = super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, col);

        // SAFE CHECK (prevents crashes)
        if (prev != null && routers != null && col > 0) {

            int before = prev[row][col - 1];
            int after = routers[row].hopCount[col - 1];

            if (before != after) {
                c.setBackground(Color.YELLOW);
            } else {
                c.setBackground(Color.WHITE);
            }

        } else {
            c.setBackground(Color.WHITE);
        }

        return c;
    }
}

    void reset() {
        log.setText("");
        panel.linkFailed = false;
        init();
        panel.repaint();
    }

    void printTablesComparison() {

    for (int i = 0; i < n; i++) {

        log.append("\n============================\n");
        log.append("Router " + routers[i].ipv6 + "\n");
        log.append("============================\n");

        log.append(String.format("%-6s %-8s %-8s\n", "Dest", "Before", "After"));

        for (int j = 0; j < n; j++) {

            String before = prevTables[i][j] == 16 ? "INF" : String.valueOf(prevTables[i][j]);
            String after  = routers[i].hopCount[j] == 16 ? "INF" : String.valueOf(routers[i].hopCount[j]);

            if (!before.equals(after)) {
                log.append(String.format("R%-5d %-8s %-8s  *\n", j, before, after));
            } else {
                log.append(String.format("R%-5d %-8s %-8s\n", j, before, after));
            }
        }

        log.append("(* = updated)\n");
    }
}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RIPngSimulator().setVisible(true));
    }
}