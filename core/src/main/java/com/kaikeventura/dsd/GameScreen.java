package com.kaikeventura.dsd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btDispatcher;
import com.badlogic.gdx.physics.bullet.collision.Collision;
import com.badlogic.gdx.physics.bullet.dynamics.btConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;

public class GameScreen implements Screen {

    // 3D Engine
    public PerspectiveCamera cam;
    public ModelBatch modelBatch;
    public Environment environment;

    // Modelos
    public Model playerModel, groundModel;
    public ModelInstance playerInstance, groundInstance;

    // PS1 Rendering
    FrameBuffer fbo;
    SpriteBatch spriteBatch;
    TextureRegion fboRegion;
    final int VIRTUAL_WIDTH = 320;
    final int VIRTUAL_HEIGHT = 240;

    // Lógica do Jogador
    Vector3 playerPosition = new Vector3(0, 1f, 0); // Começa um pouco acima do chão
    float speed = 5.0f; // Velocidade de movimento
    float rotateSpeed = 100f; // Velocidade de rotação

    // Bullet Physics
    btCollisionConfiguration collisionConfig;
    btDispatcher dispatcher;
    btDbvtBroadphase broadphase;
    btConstraintSolver solver;
    btDynamicsWorld dynamicsWorld;

    btRigidBody playerBody;
    btRigidBody groundBody;
    MyMotionState playerMotionState;

    // Classe auxiliar para sincronizar física e gráficos
    static class MyMotionState extends btMotionState {
        Matrix4 transform;

        public MyMotionState(Matrix4 transform) {
            this.transform = transform;
        }

        @Override
        public void getWorldTransform(Matrix4 worldTrans) {
            worldTrans.set(transform);
        }

        @Override
        public void setWorldTransform(Matrix4 worldTrans) {
            transform.set(worldTrans);
        }
    }

    public GameScreen() {
        Bullet.init();
    }

    @Override
    public void show() {
        // 1. ILUMINAÇÃO (Mais escura para parecer Dark Souls)
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.2f, 0.2f, 0.2f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // 2. CÂMERA
        cam = new PerspectiveCamera(67, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        cam.near = 0.5f;
        cam.far = 100f;

        // 3. MODELOS (Chão e Player)
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBatch = new ModelBatch();

        // O JOGADOR (Cubo Azul)
        playerModel = modelBuilder.createBox(1f, 2f, 1f, // Mais alto, parecendo um humano
            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            Usage.Position | Usage.Normal);
        playerInstance = new ModelInstance(playerModel);

        // O CHÃO (Plataforma Cinza Escura)
        groundModel = modelBuilder.createBox(50f, 1f, 50f, // Chão grande (50x50)
            new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)),
            Usage.Position | Usage.Normal);
        groundInstance = new ModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0, -0.5f, 0); // Move para baixo dos pés do player

        // 4. SISTEMA DE PIXELS (PS1)
        spriteBatch = new SpriteBatch();
        fbo = new FrameBuffer(Pixmap.Format.RGB565, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);

        // 5. CONFIGURAÇÃO DA FÍSICA
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -9.8f, 0));

        // Criar corpo rígido do chão (Estático)
        btCollisionShape groundShape = new btBoxShape(new Vector3(25f, 0.5f, 25f)); // Metade das dimensões (50, 1, 50)
        btRigidBody.btRigidBodyConstructionInfo groundInfo = new btRigidBody.btRigidBodyConstructionInfo(0, null, groundShape, Vector3.Zero);
        groundBody = new btRigidBody(groundInfo);
        groundBody.setWorldTransform(groundInstance.transform);
        dynamicsWorld.addRigidBody(groundBody);

        // Criar corpo rígido do jogador (Dinâmico)
        btCollisionShape playerShape = new btCapsuleShape(0.5f, 1f); // Raio 0.5, Altura da parte cilíndrica 1 (Total ~2)
        Vector3 localInertia = new Vector3();
        playerShape.calculateLocalInertia(1f, localInertia);
        playerMotionState = new MyMotionState(playerInstance.transform);
        btRigidBody.btRigidBodyConstructionInfo playerInfo = new btRigidBody.btRigidBodyConstructionInfo(1f, playerMotionState, playerShape, localInertia);
        playerBody = new btRigidBody(playerInfo);
        // Impede que a cápsula tombe (trava rotação nos eixos X e Z)
        playerBody.setAngularFactor(new Vector3(0, 1, 0));
        // Ativa o corpo para garantir que ele não comece dormindo
        playerBody.setActivationState(Collision.DISABLE_DEACTIVATION);

        // Posiciona o player inicialmente um pouco acima
        playerInstance.transform.translate(0, 5f, 0);
        playerBody.setWorldTransform(playerInstance.transform);

        dynamicsWorld.addRigidBody(playerBody);
    }

    @Override
    public void render(float delta) {
        // --- INPUTS (CONTROLE FÍSICO) ---
        // Para mover um corpo físico, aplicamos forças ou definimos velocidade linear.
        // Aqui vamos usar setLinearVelocity para um controle mais arcade/direto.

        Vector3 currentVelocity = playerBody.getLinearVelocity();
        Vector3 direction = new Vector3(0, 0, 0);

        // Rotaciona o corpo
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            playerInstance.transform.rotate(Vector3.Y, rotateSpeed * delta);
            playerBody.setWorldTransform(playerInstance.transform);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            playerInstance.transform.rotate(Vector3.Y, -rotateSpeed * delta);
            playerBody.setWorldTransform(playerInstance.transform);
        }

        // Calcula vetor de direção baseado na rotação atual
        Vector3 forward = new Vector3(0, 0, 1).rot(playerInstance.transform); // Frente

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            direction.add(forward.scl(speed));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            direction.add(forward.scl(-speed));
        }

        // Mantém a velocidade vertical (gravidade) mas altera a horizontal
        playerBody.setLinearVelocity(new Vector3(direction.x, currentVelocity.y, direction.z));


        // --- SIMULAÇÃO FÍSICA ---
        dynamicsWorld.stepSimulation(delta, 5, 1f/60f);

        // --- ATUALIZA A CÂMERA ---
        // Pega a posição atual do jogador (agora controlada pela física)
        playerInstance.transform.getTranslation(playerPosition);

        // Coloca a câmera atrás e acima do jogador (Estilo 3ª Pessoa)
        cam.position.set(playerPosition.x, playerPosition.y + 3f, playerPosition.z - 4f);
        cam.lookAt(playerPosition); // Olha para o jogador
        cam.update();

        // --- RENDERIZAÇÃO NO FBO (TELA PEQUENA) ---
        fbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1); // Fundo quase preto (Dark Souls)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        modelBatch.render(groundInstance, environment); // Desenha chão
        modelBatch.render(playerInstance, environment); // Desenha player
        modelBatch.end();
        fbo.end();

        // --- DESENHA NA TELA GRANDE ---
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        spriteBatch.begin();
        spriteBatch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        playerModel.dispose();
        groundModel.dispose();
        fbo.dispose();
        spriteBatch.dispose();

        // Dispose Physics
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
        playerBody.dispose();
        groundBody.dispose();
        // Shapes geralmente não precisam de dispose explícito se não forem compartilhados complexamente,
        // mas é boa prática se você os gerenciar separadamente.
        // Aqui eles foram criados inline ou localmente, o GC do Java cuida dos wrappers,
        // mas o objeto C++ subjacente é limpo quando o RigidBody é destruído ou explicitamente.
        // Para simplicidade neste snippet, focamos nos componentes principais do mundo.
    }
}
