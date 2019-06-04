/*  This file is part of DroidPad.
 *
 *  DroidPad is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DroidPad is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with DroidPad.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.digitalsquid.droidpad.layout;

import java.io.IOException;
import java.io.Reader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import uk.digitalsquid.droidpad.LogTag;
import uk.digitalsquid.droidpad.buttons.Button;
import uk.digitalsquid.droidpad.buttons.Layout;
import uk.digitalsquid.droidpad.buttons.ModeSpec;
import uk.digitalsquid.droidpad.buttons.Orientation;
import uk.digitalsquid.droidpad.buttons.Slider;
import uk.digitalsquid.droidpad.buttons.ToggleButton;
import uk.digitalsquid.droidpad.buttons.TouchPanel;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.RootElement;
import android.sax.TextElementListener;


public class XmlDecoder implements LogTag {
	private XmlDecoder() {}
	
	private static final SAXParserFactory factory = SAXParserFactory.newInstance();
	
	public static final ModeSpec decodeLayout(Reader stream) throws IOException {
		try {
			return internalDecodeLayout(stream);
		} catch (ParserConfigurationException e) {
			IOException e2 = new IOException("Invalid parser config");
			e2.initCause(e);
			throw e2;
		} catch (SAXException e) {
			IOException e2 = new IOException("Failed to parse XML JS layout");
			e2.initCause(e);
			throw e2;
		}
	}
	private static final ModeSpec internalDecodeLayout(Reader stream) throws ParserConfigurationException, SAXException, IOException {
		SAXParser parser = factory.newSAXParser();
		
		DocumentListener doc = new DocumentListener();
		
		parser.getXMLReader().setContentHandler(doc.getContentHandler());
		parser.getXMLReader().parse(new InputSource(stream));
		stream.close();
		return doc.getModeSpec();
	}
	
	private static final class DocumentListener {
		Layout layout;
		int mode = ModeSpec.LAYOUTS_JS;
		
		RootElement root = new RootElement("layout");
		
		public ModeSpec getModeSpec() {
			ModeSpec spec = new ModeSpec();
			spec.setLayout(layout);
			spec.setMode(mode);
			return spec;
		}
		
		public ContentHandler getContentHandler() {
			return root.getContentHandler();
		}
		
		public DocumentListener() {
			root.setElementListener(new ElementListener() {
				
				@Override
				public void start(Attributes attr) {
					String title = attr.getValue("title");
					if(title == null) title = "Custom";
					String description = attr.getValue("description");
					if(description == null) description = "Custom layout";
					
					int width = Math.abs(getInt(attr, "width", Layout.BUTTONS_X));
					int height = Math.abs(getInt(attr, "height", Layout.BUTTONS_Y));
					
					layout = new Layout(title, description, width, height);
					
					String modeText = attr.getValue("mode");
					if(modeText == null) modeText = "";
					
					if(modeText.equals("js")) mode = ModeSpec.LAYOUTS_JS;
					if(modeText.equals("joystick")) mode = ModeSpec.LAYOUTS_JS;
					
					if(modeText.equals("mouse")) mode = ModeSpec.LAYOUTS_MOUSE;
					
					if(modeText.equals("absmouse") || modeText.equals("point") || modeText.equals("pointer")) {
						mode = ModeSpec.LAYOUTS_MOUSE_ABS;
						layout.setExtraDetail(Layout.EXTRA_MOUSE_ABSOLUTE);
					}
					
					if(modeText.equals("trackpad") || modeText.equals("laptop")) {
						mode = ModeSpec.LAYOUTS_MOUSE;
						layout.setExtraDetail(Layout.EXTRA_MOUSE_TRACKPAD);
					}
					
					if(modeText.equals("slide")) mode = ModeSpec.LAYOUTS_SLIDE;
					if(modeText.equals("slideshow")) mode = ModeSpec.LAYOUTS_SLIDE;
				}
				
				@Override
				public void end() {
				}
			});
			
			Element button = root.getChild("button");
			button.setTextElementListener(new TextElementListener() {
				
				int x, y, width, height, textSize;
				boolean isReset;
				
				@Override
				public void start(Attributes attr) {
					x = getAbsInt(attr, "x", 0);
					y = getAbsInt(attr, "y", 0);
					width = getAbsInt(attr, "width", 1);
					height = getAbsInt(attr, "height", 1);
					textSize = getAbsInt(attr, "textSize", 0);
					isReset = getBoolean(attr, "reset", false);
				}
				
				@Override
				public void end(String text) {
					if(text == null) text = "Button";
					layout.add(new Button(x, y, width, height, text, textSize).setResetButton(isReset));
				}
			});
			
			Element toggle = root.getChild("toggle");
			toggle.setTextElementListener(new TextElementListener() {
				
				int x, y, width, height, textSize;
				
				@Override
				public void start(Attributes attr) {
					x = getAbsInt(attr, "x", 0);
					y = getAbsInt(attr, "y", 0);
					width = getAbsInt(attr, "width", 1);
					height = getAbsInt(attr, "height", 1);
					textSize = getAbsInt(attr, "textSize", 0);
				}
				
				@Override
				public void end(String text) {
					if(text == null) text = "Toggle";
					layout.add(new ToggleButton(x, y, width, height, text, textSize));
				}
			});
			
			Element slider = root.getChild("slider");
			slider.setElementListener(new ElementListener() {
				
				int x, y, width, height;
				Orientation type;
				
				@Override
				public void start(Attributes attr) {
					x = getAbsInt(attr, "x", 0);
					y = getAbsInt(attr, "y", 0);
					width = getAbsInt(attr, "width", 1);
					height = getAbsInt(attr, "height", 1);
					
					String typeName = attr.getValue("type");
					if(typeName == null) typeName = "both";
					if(typeName.equalsIgnoreCase("x")) type = Orientation.X;
					if(typeName.equalsIgnoreCase("y")) type = Orientation.Y;
					if(typeName.equalsIgnoreCase("horizontal")) type = Orientation.X;
					if(typeName.equalsIgnoreCase("vertical")) type = Orientation.Y;
					
					if(typeName.equalsIgnoreCase("xy")) type = Orientation.Both;
					if(typeName.equalsIgnoreCase("both")) type = Orientation.Both;
					if(typeName.equalsIgnoreCase("dual")) type = Orientation.Both;
				}
				
				@Override
				public void end() {
					layout.add(new Slider(x, y, width, height, type));
				}
			});
			
			Element panel = root.getChild("panel");
			panel.setElementListener(new ElementListener() {
				
				int x, y, width, height;
				Orientation type;
				
				@Override
				public void start(Attributes attr) {
					x = getAbsInt(attr, "x", 0);
					y = getAbsInt(attr, "y", 0);
					width = getAbsInt(attr, "width", 1);
					height = getAbsInt(attr, "height", 1);
					
					String typeName = attr.getValue("type");
					if(typeName == null) typeName = "both";
					if(typeName.equalsIgnoreCase("x")) type = Orientation.X;
					if(typeName.equalsIgnoreCase("y")) type = Orientation.Y;
					if(typeName.equalsIgnoreCase("horizontal")) type = Orientation.X;
					if(typeName.equalsIgnoreCase("vertical")) type = Orientation.Y;
					
					if(typeName.equalsIgnoreCase("xy")) type = Orientation.Both;
					if(typeName.equalsIgnoreCase("both")) type = Orientation.Both;
					if(typeName.equalsIgnoreCase("dual")) type = Orientation.Both;
				}
				
				@Override
				public void end() {
					layout.add(new TouchPanel(x, y, width, height, type));
				}
			});
		}
		
		private int getAbsInt(Attributes attr, String name, int defaultValue) {
			return Math.abs(getInt(attr, name, defaultValue));
		}
		private int getInt(Attributes attr, String name, int defaultValue) {
			String value = attr.getValue(name);
			
			try {
				return Integer.valueOf(value);
			} catch(NumberFormatException e) {
				return defaultValue;
			} catch(NullPointerException e) {
				return defaultValue;
			}
		}
		
		private boolean getBoolean(Attributes attr, String name, boolean defaultValue) {
			String value = attr.getValue(name);
			
			try {
				boolean try1 = Boolean.valueOf(value);
				if(try1) return try1;
				boolean try2 = value.equals("1");
				if(try2) return try2;
				return false;
			} catch(NumberFormatException e) {
				return defaultValue;
			} catch(NullPointerException e) {
				return defaultValue;
			}
		}
	}
}
