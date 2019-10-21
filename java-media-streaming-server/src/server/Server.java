package server;

import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

/*
 * El código se modificó de: https://github.com/JeffreyOomen/java-media-streaming-server
 */
public class Server extends JFrame implements ActionListener {


	DatagramSocket[] sockets = new DatagramSocket[2];
	InetAddress[] addresses = new InetAddress[2];
	int[] ports = new int[2];


	// GUI element however server dont'has a proper GUI
	JLabel label;

	// Video variables:
	int imagenb = 0; // Image number of the image currently transmitted
	VideoStream[] videos = new VideoStream[2]; // VideoStream object used to access video frames
	boolean[] finished = new boolean[2]; //All true if all videos are finished
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	static int FRAME_PERIOD = 100; // Frame period of the video to stream, in ms
	static int VIDEO_LENGTH = 500; // length of the video in frames

	Timer timer; // timer used to send the images at the video frame rate
	byte[] buf; // buffer used to store the images to send to the client

	// RTSP variables
	// RTSP states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	// RTSP message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;

	static int state; // RTSP Server state == INIT or READY or PLAY

	static String VideoFileName; // Video file requested from the client
	static int RTSP_ID = 123456; // ID of the RTSP session
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";

	long test = 0;

	/*
	 * Constructor
	 */
	public Server() {
		// Creates invisible frame with Server as its title
		super("Server");

		String userName = JOptionPane.showInputDialog(this, "Enter your username");
		if(!userName.equals("Berserker1523")){
			JOptionPane.showMessageDialog(this, "Invalid username");
			System.exit(0);
		}

		String userPassword = JOptionPane.showInputDialog(this, "Enter your password");
		if(!userPassword.equals("123456")){
			JOptionPane.showMessageDialog(this, "Invalid password");
			System.exit(0);
		}

		// Initialize Timer. Each frame lasts for 100ms, so every 100ms a frame
		// needs to be send. This means that when we have 500 frames total which all lasts for
		// 100ms, the total video will last 50 seconds.
		timer = new Timer(FRAME_PERIOD, this);
		timer.setInitialDelay(0); // No initial delay when the video starts
		// It is possible that multiple frames are queued. To avoid that each frame is then
		// send with no delay between them, coalescing is set to true to avoid this problem.
		timer.setCoalesce(true);

		// Allocate memory for the sending buffer
		buf = new byte[15000];

		// Handler to close the main window
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				// Stop the timer and exit
				timer.stop();
				System.exit(0);
			}
		});

		// GUI:
		label = new JLabel("Send frame #", JLabel.CENTER);
		getContentPane().add(label, BorderLayout.CENTER);
	}

	/*
	 *  The main method which will be invoked with arguments by the
	 *  command line.
	 */
	public static void main(String argv[]) throws Exception {
		// Create a Server object
		Server theServer = new Server();

		// Show GUI:
		theServer.pack(); //Resize window so that alle GUI elements take as much space as they need
		theServer.setVisible(true);


		theServer.addresses[0] = InetAddress.getByName("230.0.0.1");
		theServer.sockets[0] = new DatagramSocket();
		System.out.println("SERVER MADE RTP SOCKET WITH PORT NUMBER: " + theServer.sockets[0].getPort());

		theServer.addresses[1] = InetAddress.getByName("230.0.0.2");
		theServer.sockets[1] = new DatagramSocket();
		System.out.println("SERVER MADE RTP SOCKET WITH PORT NUMBER: " + theServer.sockets[1].getPort());
		
		theServer.ports[0] = 4446;
		theServer.ports[1] = 4446;

		state = INIT;
		state = READY;
		System.out.println("New state: READY");

		theServer.videos[0] = new VideoStream("movie.Mjpeg");
		theServer.videos[1] = new VideoStream("movie.Mjpeg");

		theServer.timer.start();
		state = PLAYING;
		System.out.println("New RTSP state: PLAYING");

		while(true){
			if(!theServer.timer.isRunning()){
				for (DatagramSocket socket : theServer.sockets ) {
					socket.close();
				}
				System.exit(0);
			}
		}
	}

	/*
	 * This method will be invoked every 100ms by the timer
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		for(int i = 0; i<videos.length; i++){
			// If the current image number is less than the total amount of images (frames) of the video (500)
			if (imagenb < VIDEO_LENGTH) {
				// Update current image number
				imagenb++;

				try {
					// Get next frame to send from the video, as well as its size
					int image_length = videos[i].getnextframe(buf);


					int timeStamp = ((int)(new Date()).getTime());

					RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, timeStamp, buf, image_length);

					System.out.println("Timestamp: " + timeStamp);
					test++;

					// Get to total length of the full rtp packet to send (so data + headers)
					int packet_length = rtp_packet.getlength();

					// Retrieve the packet bitstream and store it in an array of bytes
					byte[] packet_bits = new byte[packet_length];
					rtp_packet.getpacket(packet_bits);

					// Send the packet as a DatagramPacket over the UDP socket.
					// The RTP destination port was specified by the client, in this case 25 000 and is extracted
					// in the parse_RTSP_request() method.
					DatagramPacket sendingDatagramPacket = new DatagramPacket(packet_bits, packet_length, addresses[i], ports[i]); 

					sockets[i].send(sendingDatagramPacket); //send the packet over the RTP socket of the server to the client's socket
					rtp_packet.printheader();

					// Update GUI
					label.setText("Send frame #" + imagenb);
				} catch (Exception ex) {
					System.out.println("Exception caught: " + ex);
					System.exit(0);
				}
			} else {
				finished[i] = true;
				// If we have reached the end of the video file, stop the timer
				if(finished[0] && finished[1]){
					timer.stop();
				}
			}
		}
	}
}
