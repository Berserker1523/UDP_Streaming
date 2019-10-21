package client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;

/*
 * El código se modificó de: https://github.com/JeffreyOomen/java-media-streaming-client
 */

public class Client {
	//
	boolean playedFirstTime = false;

	// GUI elements
	JFrame f = new JFrame("Client");
	JButton setupButton = new JButton("Connect");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton tearButton = new JButton("Close");
	JPanel mainPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	JLabel iconLabel = new JLabel();
	ImageIcon icon;

	// RTP variables:
	DatagramPacket rcvdp; // UDP packet received from the server
	MulticastSocket multicastSocket; // socket to be used to send and receive UDP packets
	InetAddress multicastIPAddr;
	static int RCV_PORT = 4446; // port where the client will receive the RTP packets

	Timer timer; // timer used to receive data from the UDP socket
	byte[] buf; // buffer used to store data received from the server

	// RTSP variables
	// RTSP States
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	static int state; //state == INIT or READY or PLAYING
	
	static String VideoFileName; // video file to request to the server

	final static String CRLF = "\r\n"; // To end header lines

	// Video constants:
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

	/**
	 * The constructor of the client which will build the GUI elements
	 */
	public Client() {

		// Frame
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				try {
					multicastSocket.leaveGroup(multicastIPAddr);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				multicastSocket.close();
				System.exit(0);
			}
		});

		// Buttons
		buttonPanel.setLayout(new GridLayout(1, 0));
		buttonPanel.add(setupButton);
		buttonPanel.add(playButton);
		buttonPanel.add(pauseButton);
		buttonPanel.add(tearButton);
		setupButton.addActionListener(new setupButtonListener());
		playButton.addActionListener(new playButtonListener());
		pauseButton.addActionListener(new pauseButtonListener());
		tearButton.addActionListener(new tearButtonListener());

		// Image display label
		iconLabel.setIcon(null);

		// frame layout
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel);
		mainPanel.add(buttonPanel);
		iconLabel.setBounds(0, 0, 380, 280);
		buttonPanel.setBounds(0, 280, 380, 50);

		f.getContentPane().add(mainPanel, BorderLayout.CENTER);
		f.setSize(new Dimension(390, 370));
		f.setVisible(true);

		// Initialize the timer with a delay of 20ms
		timer = new Timer(20, new timerListener());
		timer.setInitialDelay(0); //The delay after the timer starts for the first time
		timer.setCoalesce(true); //Avoid that many events will queue after each other

		// Allocate enough memory for the buffer used to receive data from the server
		buf = new byte[15000];
	}

	/*
	 * The main method which will be invoked by the command line
	 */
	public static void main(String argv[]) throws Exception {
		// Create a Client object
		Client theClient = new Client();

		// Get video filename to request
		VideoFileName = "movie.Mjpeg";

		state = INIT;
	}

	/*
	 * This class will handle the SETUP button click
	 * Will setup a UDP connection
	 */
	class setupButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Setup Button pressed!");
			if (state == INIT) {
				state = READY;
				// Initialize non-blocking UDPsocket that will be used to receive data
				try {
					// Construct a new DatagramSocket (UDP) to receive packets
					String inPortString = JOptionPane.showInputDialog(f, "Enter the port you want to connect\nLet blank to default");
					int inPort = 0;
					if(inPortString.equals("")){
						inPort = RCV_PORT;
					}
					else{
						inPort = Integer.parseInt(inPortString);
					}
					System.out.println("Selected port " + inPort);
					multicastSocket = new MulticastSocket(inPort); // Setting up port for the client
					
					
					String inAddrString = JOptionPane.showInputDialog(f, "Enter the multicast direction you want to connect\nLet blank to default");
		
					if(inAddrString.equals("")){
						inAddrString = "230.0.0.1";
					}
				
					System.out.println("Selected Address " + inAddrString);
					
					multicastIPAddr = InetAddress.getByName(inAddrString);
					multicastSocket.joinGroup(multicastIPAddr);
					System.out.println("CLIENT CREATED A MULTICAST SOCKET");

				} catch (IOException ioe) {
					System.out.println("IO exception: " + ioe);
					System.exit(0);
				}
			}
		}
	}

	/*
	 * This class will handle the PLAY button click via RTSP
	 */
	class playButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Play Button pressed !");

			if (state == READY) {
				System.out.println("llegue :v");
				state = PLAYING;
				timer.start();
			} 		
		}
	}

	/*
	 * This class will handle clicking on the pause button
	 */
	class pauseButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Pause Button pressed !");
			if (state == PLAYING) {

				state = READY;
				timer.stop();	
			}		
		}
	}

	/*
	 * This class will handle clicking on the tear down button
	 */
	class tearButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Teardown Button pressed !");

			state = INIT;
			timer.stop();
			System.exit(0);

		}
	}

	/*
	 * The actionPerformed method will be invoked by the timer
	 * continuously until the timer stops.
	 */
	class timerListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			// Construct a DatagramPacket to receive data from the UDP socket
			// The data will be put in the buffer (buf) which has a length of buf.lenght
			rcvdp = new DatagramPacket(buf, buf.length);

			try {
				// Receive the DataPacket from the socket with the video data from the socket
				
				multicastSocket.receive(rcvdp);
				
				// Create an RTPpacket object from the DataPacket
				RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

				// Print important header fields of the RTP packet received:
				System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getsequencenumber() + " TimeStamp "
						+ rtp_packet.gettimestamp() + " ms, of type " + rtp_packet.getpayloadtype());

				// Print header bit stream:
				rtp_packet.printheader();

				// Get the payload bitstream from the RTPpacket object
				int payload_length = rtp_packet.getpayload_length(); // Will be 26 in this case because the type was 26: MJPEG
				System.out.println("Payload is: " + payload_length);
				byte[] payload = new byte[payload_length];
				rtp_packet.getpayload(payload);

				// Get an Image object from the payload bitstream
				Toolkit toolkit = Toolkit.getDefaultToolkit();
				Image image = toolkit.createImage(payload, 0, payload_length);

				// Display the image as an ImageIcon object
				icon = new ImageIcon(image);
				iconLabel.setIcon(icon);
			} catch (InterruptedIOException iioe) {
				System.out.println("Exception caught: " + iioe);
			} catch (IOException ioe) {
				System.out.println("Exception caught: " + ioe);
			}
		}
	}

}// end of Class Client
