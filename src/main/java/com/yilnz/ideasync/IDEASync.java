package com.yilnz.ideasync;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class IDEASync extends DefaultHandler {
	public static final String writePath = "/Users/zyl/Documents/itaojingit/ideasync/idea_plugin.txt";
	public static final String writePath2 = "/Users/zyl/Documents/itaojingit/ideasync/idea_plugin_ids.txt";
	private boolean id = false;
	private boolean name = false;
	private Plugin current;

	private static class Plugin{
		String id;
		String name;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Plugin plugin = (Plugin) o;
			return Objects.equals(id, plugin.id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id);
		}

		@Override
		public String toString() {
			return "Plugin{" +
					"id='" + id + '\'' +
					", name='" + name + '\'' +
					'}';
		}
	}

	@Override
	public void startDocument() throws SAXException {

	}

	private Set<Plugin> plugins = new HashSet<>();

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if (localName.equals("id")) {
			id = true;
		} else if (localName.equals("name")) {
			name = true;
		}
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (id) {
			final String id = new String(ch, start, length).trim();

			if(id.startsWith("com.intellij.plugins") || id.startsWith("org.jetbrains")){
				current = null;
			}else {
				final Plugin plugin = new Plugin();
				plugin.id = id;
				plugins.add(plugin);
				current = plugin;
			}
		}else if(name && current != null){
			current.name = new String(ch, start, length).trim();
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (localName.equals("id")) {
			id = false;
		} else if (localName.equals("name")) {
			name = false;
		}
	}


	public void sync() {
		try {
			Files.list(Paths.get("/Users/zyl/Library/Application Support/IntellijIdea2018.3/")).forEach(path -> {
				if (path.toFile().isDirectory()) {
					try {
						Files.walk(path, FileVisitOption.FOLLOW_LINKS).forEach(e -> {
							parseXml(e);
						});
					} catch (IOException e) {
						e.printStackTrace();
					}

				} else {
					parseXml(path);
				}
			});
			final String disabled = new String(Files.readAllBytes(Paths.get("/Users/zyl/Library/Preferences/IntelliJIdea2018.3/disabled_plugins.txt")));
			StringTokenizer stringTokenizer = new StringTokenizer(disabled, "\n");
			List<Plugin> disabledIds = new ArrayList<>();
			while (stringTokenizer.hasMoreElements()) {
				final Plugin plugin = new Plugin();
				plugin.id = (String) stringTokenizer.nextElement();
				disabledIds.add(plugin);
			}
			plugins.removeAll(disabledIds);
			final String s1 = plugins.stream().map(e -> String.format("%s --------------------------------- %s" , (e.name == null ? e.id : e.name) , e.id)).collect(Collectors.toList()).toString().replaceAll("\\[|\\]", "").replaceAll("\\s*,\\s*", "\n").trim();
			//final String s2 = plugins.stream().map(e -> e.id).collect(Collectors.toList()).toString().replaceAll("\\[|\\]", "").replaceAll("\\s*,\\s*", "\n").trim();
			Files.write(Paths.get(writePath), s1.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
			//Files.write(Paths.get(writePath2), s2.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseXml(Path path) {
		try {
			if (path.toString().endsWith(".jar") || path.toString().endsWith(".zip")) {
				JarFile jarFile = new JarFile(path.toFile());
				JarEntry jarEntry = jarFile.getJarEntry("META-INF/plugin.xml");
				if (jarEntry == null) {
					return;
				}
				final InputStream inputStream = jarFile.getInputStream(jarEntry);
				final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
				final BufferedReader br = new BufferedReader(inputStreamReader);
				XMLReader parser = XMLReaderFactory.createXMLReader();
				parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				parser.setContentHandler(this);
				parser.parse(new InputSource(br));
			}
		} catch (IOException | SAXException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new IDEASync().sync();
	}
}
