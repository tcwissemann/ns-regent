package frontend;

import java.awt.Font;

import javax.swing.*;

public class NSRegentGUI extends JFrame {
	public NSRegentGUI() {
		// render frame and title
		super("NSRegent");
		
		// set size and make un-resizable
		setSize(540, 570);
		setResizable(false);
		
		// allows control of position & size of elements
		setLayout(null);
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		// center the GUI to the screen
		setLocationRelativeTo(null);
		
		addGuiComponents();
	}
	
	private void addGuiComponents() {
		// create title text
		JLabel titleLabel = new JLabel("NSREGENT");
		titleLabel.setFont(new Font("Dialog", Font.BOLD, 32));
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setBounds(0, 40, 540, 39);
		add(titleLabel);
		 
		// create nation name field
	    JLabel nationLabel = new JLabel("Nation Name:");
	    nationLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
	    nationLabel.setBounds(30, 120, 100, 20);
	    add(nationLabel);

	    JTextField nationText = new JTextField();
	    nationText.setFont(new Font("Dialog", Font.PLAIN, 14));
	    nationText.setBounds(30, 145, 480, 35);
	    add(nationText);
	    
	    JLabel passwordLabel = new JLabel("Password:");
	    passwordLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
	    passwordLabel.setBounds(30, 195, 100, 20);
	    add(passwordLabel);

	    JPasswordField passwordField = new JPasswordField();
	    passwordField.setBounds(30, 220, 480, 35);
	    add(passwordField);
	    
	    JLabel serviceLabel = new JLabel("Service:");
	    serviceLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
	    serviceLabel.setBounds(30, 270, 100, 20);
	    add(serviceLabel);

	    String[] services = {"Select Service", "Recruitment", "Endorsement", "Campaign"};
	    JComboBox<String> serviceComboBox = new JComboBox<>(services);
	    serviceComboBox.setBounds(30, 295, 480, 35);
	    add(serviceComboBox);
	    
	    JLabel modeLabel = new JLabel("Mode Package:");
	    modeLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
	    modeLabel.setBounds(30, 345, 120, 20);
	    add(modeLabel);

	    JComboBox<String> modeComboBox = new JComboBox<>();
	    modeComboBox.setBounds(30, 370, 480, 35);
	    modeComboBox.setEnabled(false);
	    add(modeComboBox);
	    
	    
	    // Add action listener for dependent dropdown
	    serviceComboBox.addActionListener(e -> {
	        String selectedService = (String) serviceComboBox.getSelectedItem();
	        modeComboBox.removeAllItems();
	        modeComboBox.setEnabled(true);

	        switch (selectedService) {
	            case "Recruitment":
	                modeComboBox.addItem("Standard Recruitment");
	                modeComboBox.addItem("Mass Recruitment");
	                modeComboBox.addItem("Targeted Recruitment");
	                break;
	            case "Endorsement":
	                modeComboBox.addItem("Auto-Endorsement");
	                modeComboBox.addItem("Cross Endorsement");
	                modeComboBox.addItem("Regional Endorsement");
	                break;
	            case "Campaign":
	                modeComboBox.addItem("Vote Campaign");
	                modeComboBox.addItem("Support Campaign");
	                modeComboBox.addItem("Custom Campaign");
	                break;
	            default:
	                modeComboBox.setEnabled(false);
	                break;
	        }
	    });

	    // Submit Button
	    JButton submitButton = new JButton("Submit");
	    submitButton.setFont(new Font("Dialog", Font.BOLD, 16));
	    submitButton.setBounds(30, 445, 480, 45);
	    add(submitButton);
	}
}
