/*
 * Copyright (c) 2009-2011 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.monkeyzone;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.ChaseCamera;
import com.jme3.monkeyzone.controls.DefaultHUDControl;
import com.jme3.monkeyzone.controls.UserInputControl;
import com.jme3.monkeyzone.messages.ChatMessage;
import com.jme3.monkeyzone.messages.StartGameMessage;
import com.jme3.network.connection.Client;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.renderer.RenderManager;
import com.jme3.system.AppSettings;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.dynamic.LabelCreator;
import de.lessvoid.nifty.controls.textfield.controller.TextFieldControl;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The client Main class, also the screen controller for most parts of the GUI
 * @author normenhansen
 */
public class ClientMain extends SimpleApplication implements ScreenController {

    private static ClientMain app;

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String string = args[i];
            if ("-server".equals(string)) {
                ServerMain.main(args);
                return;
            }
        }
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(Globals.SCENE_FPS);
        settings.setSettingsDialogImage("/Interface/Images/splash-small.jpg");
        settings.setTitle("MonkeyZone");
        Util.registerSerializers();
        Util.setLogLevels(true);
        app = new ClientMain();
        app.setSettings(settings);
        app.setPauseOnLostFocus(false);
        app.start();
    }
    private WorldManager worldManager;
    private ClientEffectsManager effectsManager;
    private Nifty nifty;
    private NiftyJmeDisplay niftyDisplay;
    private TextRenderer statusText;
    private Client client;
    private ClientNetListener listenerManager;
    private BulletAppState bulletState;
    private ChaseCamera chaseCam;

    @Override
    public void simpleInitApp() {
        startNifty();
        client = new Client();
        bulletState = new BulletAppState();
        if (Globals.PHYSICS_THREADED) {
            bulletState.setThreadingType(BulletAppState.ThreadingType.PARALLEL);
        }
        getStateManager().attach(bulletState);
        bulletState.getPhysicsSpace().setDeterministic(Globals.PHYSICS_DETERMINISTIC);
        bulletState.getPhysicsSpace().setAccuracy(Globals.PHYSICS_FPS);
        inputManager.setCursorVisible(true);
        flyCam.setEnabled(false);
        chaseCam = new ChaseCamera(cam, inputManager);
        chaseCam.setSmoothMotion(true);
        chaseCam.setChasingSensitivity(100);
        chaseCam.setTrailingEnabled(true);

        worldManager = new WorldManager(this, rootNode, bulletState.getPhysicsSpace(), client);
        //adding/creating controls later attached to user controlled spatial
        worldManager.addUserControl(chaseCam);
        worldManager.addUserControl(new UserInputControl(inputManager, cam));
        worldManager.addUserControl(new DefaultHUDControl(nifty.getScreen("default_hud")));
        effectsManager = new ClientEffectsManager(assetManager, audioRenderer, worldManager);
        //register effects manager with sync manager so that messages can apply their data
        worldManager.getSyncManager().addObject(-2, effectsManager);
        listenerManager = new ClientNetListener(this, client, worldManager, effectsManager);
    }

    /**
     * starts the nifty gui system
     */
    private void startNifty() {
        guiNode.detachAllChildren();
        guiNode.attachChild(fpsText);
        niftyDisplay = new NiftyJmeDisplay(assetManager,
                inputManager,
                audioRenderer,
                guiViewPort);
        nifty = niftyDisplay.getNifty();
        try {
            nifty.fromXml("Interface/ClientUI.xml", "load_game", this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        statusText = nifty.getScreen("load_game").findElementByName("layer").findElementByName("panel").findElementByName("status_text").getRenderer(TextRenderer.class);
        guiViewPort.addProcessor(niftyDisplay);
    }

    /**
     * sets the status text of the main login view, threadsafe
     * @param text
     */
    public void setStatusText(final String text) {
        enqueue(new Callable<Void>() {

            public Void call() throws Exception {
                statusText.changeText(text);
                return null;
            }
        });
    }

    /**
     * updates the list of players in the lobby gui, threadsafe
     */
    public void updatePlayerData() {
        Logger.getLogger(ClientMain.class.getName()).log(Level.INFO, "Updating player data");
        enqueue(new Callable<Void>() {

            public Void call() throws Exception {
                Screen screen = nifty.getScreen("lobby");
                Element panel = screen.findElementByName("layer").findElementByName("panel").findElementByName("players_panel").findElementByName("players_list").findElementByName("panel");
                List<PlayerData> players = PlayerData.getHumanPlayers();
                for (Iterator<Element> it = new LinkedList<Element>(panel.getElements()).iterator(); it.hasNext();) {
                    Element element = it.next();
                    element.markForRemoval();//disable();
                }
                LabelCreator labelCreator = new LabelCreator("unknown player");
                labelCreator.setStyle("my-listbox-item-style");
                for (Iterator<PlayerData> it = players.iterator(); it.hasNext();) {
                    PlayerData data = it.next();
                    Logger.getLogger(ClientMain.class.getName()).log(Level.INFO, "List player {0}", data);
                    labelCreator.setText(data.getStringData("name"));
                    labelCreator.create(nifty, screen, panel);
                }
                return null;
            }
        });
    }

    /**
     * add text to chat window, threadsafe
     * @param text
     */
    public void addChat(final String text) {
        enqueue(new Callable<Void>() {

            public Void call() throws Exception {
                Screen screen = nifty.getScreen("lobby");
                Element panel = screen.findElementByName("layer").findElementByName("bottom_panel").findElementByName("chat_panel").findElementByName("chat_list").findElementByName("chat_list_panel");
                LabelCreator labelCreator = new LabelCreator(text);
                labelCreator.setStyle("my-listbox-item-style");
                labelCreator.create(nifty, screen, panel);
                return null;
            }
        });
    }

    /**
     * gets the text currently entered in the textbox and sends it as a chat message
     */
    public void sendChat() {
        Logger.getLogger(ClientMain.class.getName()).log(Level.INFO, "Send chat message");
        enqueue(new Callable<Void>() {

            public Void call() throws Exception {
                Screen screen = nifty.getScreen("lobby");
                TextFieldControl control = screen.findElementByName("layer").findElementByName("bottom_panel").findElementByName("chat_panel").findElementByName("chat_bottom_bar").findElementByName("chat_text").getControl(TextFieldControl.class);
                String text = control.getText();
                sendMessage(text);
                control.setText("");
                return null;
            }
        });
    }

    //FIXME: nifty cannot find sendChat() when sendChat(String text) is existing too
    public void sendMessage(String text) {
        try {
            client.send(new ChatMessage(text));
        } catch (IOException ex) {
            Logger.getLogger(ClientMain.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * connect to server (called from gui)
     */
    public void connect() {
        //TODO: not connect when already trying..
        final String userName = nifty.getScreen("load_game").findElementByName("layer").findElementByName("panel").findElementByName("username_text").getControl(TextFieldControl.class).getText();
        if (userName.trim().length() == 0) {
            setStatusText("Username invalid");
            return;
        }
        listenerManager.setName(userName);
        statusText.changeText("Connecting..");
        try {
            client.connect(Globals.DEFAULT_SERVER, Globals.DEFAULT_PORT_TCP, Globals.DEFAULT_PORT_UDP);
            client.start();
        } catch (IOException ex) {
            setStatusText(ex.getMessage());
            Logger.getLogger(ClientMain.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * brings up the lobby display
     */
    public void lobby() {
        chaseCam.setDragToRotate(false);
        inputManager.setCursorVisible(true);
        nifty.gotoScreen("lobby");
    }

    /**
     * send message to start selected game
     */
    public void startGame() {
        //TODO: map selection
        try {
            client.send(new StartGameMessage("Scenes/MonkeyZone.j3o"));
        } catch (IOException ex) {
            Logger.getLogger(ClientMain.class.getName()).log(Level.SEVERE, "Cannot send start game message: {0}", ex);
        }
    }

    /**
     * loads a level, basically does everything on a seprate thread except
     * updating the UI and attaching the level
     * @param name
     * @param modelNames
     */
    public void loadLevel(final String name, final String[] modelNames) {
        final TextRenderer statusText = nifty.getScreen("load_level").findElementByName("layer").findElementByName("panel").findElementByName("status_text").getRenderer(TextRenderer.class);
        if (name.equals("null")) {
            enqueue(new Callable<Void>() {

                public Void call() throws Exception {
                    worldManager.closeLevel();
                    lobby();
                    return null;
                }
            });
            return;
        }
        new Thread(new Runnable() {

            public void run() {
                try {
                    enqueue(new Callable<Void>() {

                        public Void call() throws Exception {
                            nifty.gotoScreen("load_level");
                            statusText.changeText("Loading Terrain..");
                            return null;
                        }
                    }).get();
                    worldManager.loadLevel(name);
                    enqueue(new Callable<Void>() {

                        public Void call() throws Exception {
                            statusText.changeText("Creating NavMesh..");
                            return null;
                        }
                    }).get();
                    worldManager.createNavMesh();
                    enqueue(new Callable<Void>() {

                        public Void call() throws Exception {
                            statusText.changeText("Loading Models..");
                            return null;
                        }
                    }).get();
                    worldManager.preloadModels(modelNames);
                    enqueue(new Callable<Void>() {

                        public Void call() throws Exception {
                            worldManager.attachLevel();
                            statusText.changeText("Done Loading!");
                            nifty.gotoScreen("default_hud");
                            inputManager.setCursorVisible(false);
                            chaseCam.setDragToRotate(false);
                            return null;
                        }
                    }).get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void bind(Nifty nifty, Screen screen) {
    }

    public void onStartScreen() {
    }

    public void onEndScreen() {
    }

    @Override
    public void simpleUpdate(float tpf) {
        worldManager.update(tpf);
        effectsManager.update(tpf);
    }

    @Override
    public void simpleRender(RenderManager rm) {
    }

    @Override
    public void destroy() {
        try {
            client.disconnect();
        } catch (Exception ex) {
            Logger.getLogger(ClientMain.class.getName()).log(Level.SEVERE, null, ex);
        }
        super.destroy();
    }
}
