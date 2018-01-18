// Question Generation via Overgenerating Transformations and Ranking
// Copyright (c) 2008, 2009 Carnegie Mellon University.  All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Michael Heilman
//	  Carnegie Mellon University
//	  mheilman@cmu.edu
//	  http://www.cs.cmu.edu/~mheilman



package edu.cmu.ark.nlp.question;

import com.sun.net.httpserver.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.net.URLDecoder;
import java.util.concurrent.*;
import java.io.*;


//import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.trees.Tree;

public class QuestionAskerServer {

    /**
    * The main handler for taking an annotation request, and annotating it.
    */
   protected class QuestionAskerHandler implements HttpHandler {
        private static final int SLURP_BUFFER_SIZE = 16384;
        protected QuestionTransducer qt;
        protected InitialTransformationStep trans;
        protected QuestionRanker qr = null;
        protected Tree parsed;
        protected boolean printVerbose = false;
        protected String modelPath = null;
        protected boolean preferWH = false;
        protected boolean doNonPronounNPC = false;
        protected boolean doPronounNPC = true;
        protected Integer maxLength = 1000;
        protected boolean downweightPronouns = false;
        protected boolean avoidFreqWords = false;
        protected boolean dropPro = true;
        protected boolean justWH = false;
        protected boolean debug = false;

        public QuestionAskerHandler(String[] args) {
            this.qt = new QuestionTransducer();
            this.trans = new InitialTransformationStep();
            this.qt.setAvoidPronounsAndDemonstratives(false);
            
            //pre-load
            QuestionUtil.getInstance();
            
            for(int i=0;i<args.length;i++){
                if(args[i].equals("--debug")){
                    this.debug = true;
                }else if(args[i].equals("--verbose")){
                    this.printVerbose = true;
                }else if(args[i].equals("--model")){ //ranking model path
                    this.modelPath = args[i+1]; 
                    i++;
                }else if(args[i].equals("--keep-pro")){
                    this.dropPro = false;
                }else if(args[i].equals("--downweight-pro")){
                    this.dropPro = false;
                    this.downweightPronouns = true;
                }else if(args[i].equals("--downweight-frequent-answers")){
                    this.avoidFreqWords = true;
                }else if(args[i].equals("--prefer-wh")){  
                    this.preferWH = true;
                }else if(args[i].equals("--just-wh")){  
                    this.justWH = true;
                }else if(args[i].equals("--full-npc")){  
                    this.doNonPronounNPC = true;
                }else if(args[i].equals("--no-npc")){  
                    this.doPronounNPC = false;
                }else if(args[i].equals("--max-length")){  
                    this.maxLength = new Integer(args[i+1]);
                    i++;
                }
            }
            
            this.qt.setAvoidPronounsAndDemonstratives(this.dropPro);
            this.trans.setDoPronounNPC(this.doPronounNPC);
            this.trans.setDoNonPronounNPC(this.doNonPronounNPC);
            
            System.out.println("Loading question ranking models from "+this.modelPath+"...");
            this.qr = new QuestionRanker();
            this.qr.loadModel(this.modelPath);
            System.out.println("Done loading model.");
        }
      private Reader encodedInputStreamReader(InputStream stream, String encoding) throws IOException {
        // InputStreamReader doesn't allow encoding to be null;
        if (encoding == null) {
          return new InputStreamReader(stream);
        } else {
          return new InputStreamReader(stream, encoding);
        }
      }

      private String slurpReader(Reader reader) {
        StringBuilder buff = new StringBuilder();
        try {
          char[] chars = new char[SLURP_BUFFER_SIZE];
          BufferedReader r = new BufferedReader(reader);
          while (true) {
            int amountRead = r.read(chars, 0, SLURP_BUFFER_SIZE);
            if (amountRead < 0) {
              break;
            }
            buff.append(chars, 0, amountRead);
          }
          r.close();
        } catch (Exception e) {
          throw new RuntimeIOException("slurpReader IO problem", e);
        }
        return buff.toString();
      }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
      //httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
      //httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");

      // Get text.
      String encoding;
      String text;
      try {
            String defaultEncoding = "UTF-8";
            // Get the encoding
            Headers h = httpExchange.getRequestHeaders();
            if (h.containsKey("Content-type")) {
              String[] charsetPair = Arrays.stream(h.getFirst("Content-type").split(";"))
                  .map(x -> x.split("="))
                  .filter(x -> x.length > 0 && "charset".equals(x[0]))
                  .findFirst().orElse(new String[]{"charset", defaultEncoding});
              if (charsetPair.length == 2) {
                encoding = charsetPair[1];
              } else {
                encoding = defaultEncoding;
              }
            } else {
              encoding = defaultEncoding;
            }

            text = slurpReader(encodedInputStreamReader(httpExchange.getRequestBody(), encoding));

            // Remove the \ and + characters that mess up the URL decoding.
            text = text.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
            text = text.replaceAll("\\+", "%2B");
            text = URLDecoder.decode(text, encoding).trim();
            System.out.println("Recv: " + text);
      } catch (Exception e) {
        e.printStackTrace();
        respondError("Could not handle incoming request", httpExchange);
        return;
      }

      try {
          // Get output
          //ByteArrayOutputStream os = new ByteArrayOutputStream();
          //StanfordCoreNLP.createOutputter(props, options).accept(completedAnnotation, os);
          //os.close();
          //byte[] response = os.toByteArray();
          byte[] response = text.getBytes();//os.toByteArray();

          String contentType = "text/plain";
            if (contentType.equals("application/json") || contentType.startsWith("text/")) {
              contentType += ";charset=" + encoding;
            }
            httpExchange.getResponseHeaders().add("Content-type", contentType);
            httpExchange.getResponseHeaders().add("Content-length", Integer.toString(response.length));
            httpExchange.sendResponseHeaders(200, response.length);
            httpExchange.getResponseBody().write(response);
            httpExchange.close();
      } catch (Exception e) {
        // Print the stack trace for debugging
        e.printStackTrace();
        // Return error message.
        respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
      }
    }

      private void respondError(String response, HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add("Content-type", "text/plain");
        httpExchange.sendResponseHeaders(500, response.length());
        httpExchange.getResponseBody().write(response.getBytes());
        httpExchange.close();
      }
 }


	public static void main(String[] args) {

		int port = 5558;
		for(int i=0;i<args.length;i++){
			if(args[i].equals("--port")){  
				port = new Integer(args[i+1]);
				i++;
			}
		}
		
        HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress(8000), 0);
			server.createContext("/ask", new QuestionAskerHandler(args));
	        server.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Server ready...listening on Port: "+ port);
    }
}
