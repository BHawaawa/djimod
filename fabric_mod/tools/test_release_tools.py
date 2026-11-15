"""Regression checks for cross-loader release labeling and bytecode safety."""
import json
import pathlib
import tempfile
import unittest
import zipfile

from verify_release_jar import verify


class ReleaseValidation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.jar = pathlib.Path(self.temp.name) / 'release.jar'

    def tearDown(self):
        self.temp.cleanup()

    def fixture(self, loader='Fabric', minecraft='1.20.1', java=17, qa=False, missing_entry=False):
        with zipfile.ZipFile(self.jar, 'w') as jar:
            header = b'\xca\xfe\xba\xbe\x00\x00' + (java + 44).to_bytes(2, 'big')
            if not missing_entry:
                jar.writestr('com/example/avatadrone/AvataDrone.class', header)
            jar.writestr('com/example/avatadrone/AvataDroneClient.class', header)
            if qa:
                jar.writestr('com/example/avatadrone/qa/RenderQa.class', header)
            if loader == 'Fabric':
                jar.writestr('fabric.mod.json', json.dumps({
                    'id': 'djimod', 'version': '1.0.33', 'depends': {'minecraft': minecraft},
                    'entrypoints': {'main': ['com.example.avatadrone.AvataDrone'],
                                    'client': ['com.example.avatadrone.AvataDroneClient']}}))
            else:
                entrypoint = 'NeoForgeEntrypoint' if loader == 'NeoForge' else 'ForgeEntrypoint'
                jar.writestr('com/example/avatadrone/' + entrypoint + '.class', header)
                name = 'META-INF/neoforge.mods.toml' if loader == 'NeoForge' else 'META-INF/mods.toml'
                lid = 'neoforge' if loader == 'NeoForge' else 'forge'
                jar.writestr(name, f'''[[mods]]
modId="djimod"
version="1.0.33"
[[dependencies.djimod]]
modId="minecraft"
versionRange="[{minecraft}]"
[[dependencies.djimod]]
modId="{lid}"
versionRange="[1,)"
''')

    def test_valid_loader_specific_releases(self):
        for loader, mc, java in [('Fabric', '1.16.5', 8), ('Forge', '1.20.1', 17),
                                 ('NeoForge', '1.21.1', 21), ('NeoForge', '26.3', 25)]:
            with self.subTest(loader=loader, minecraft=mc):
                self.fixture(loader, mc, java)
                self.assertTrue(verify(self.jar, loader, mc, '1.0.33'))

    def test_cannot_rename_fabric_to_other_loader(self):
        self.fixture()
        for loader in ['Forge', 'NeoForge']:
            with self.assertRaises(ValueError):
                verify(self.jar, loader, '1.20.1', '1.0.33')

    def test_cannot_rename_game_or_release_version(self):
        for loader in ['Fabric', 'Forge', 'NeoForge']:
            self.fixture(loader)
            for mc, version in [('26.3', '1.0.33'), ('1.20.1', '9.9.9')]:
                with self.assertRaises(ValueError):
                    verify(self.jar, loader, mc, version)

    def test_no_qa_or_missing_entrypoint(self):
        for arguments in [{'qa': True}, {'missing_entry': True}]:
            self.fixture(**arguments)
            with self.assertRaises(ValueError):
                verify(self.jar, 'Fabric', '1.20.1', '1.0.33')

    def test_bytecode_must_run_on_target_java(self):
        self.fixture(minecraft='1.16.5', java=17)
        with self.assertRaises(ValueError):
            verify(self.jar, 'Fabric', '1.16.5', '1.0.33')

    def test_latest_requested_matrix_and_build_routes(self):
        root = pathlib.Path(__file__).resolve().parents[1]
        matrix = json.loads((root / 'release/matrix.json').read_text('utf-8'))
        expected = {('Forge', mc) for mc in ['1.16.5', '1.17.1', '1.18.2', '1.19.2', '1.20.1']}
        expected |= {('Fabric', mc) for mc in ['1.16.5', '1.17.1', '1.18.2', '1.19.2', '1.20.1', '1.21.1']}
        expected |= {('NeoForge', mc) for mc in ['1.21.1', '26.3']}
        targets = matrix['targets']
        self.assertEqual(len(targets), len(expected))
        self.assertEqual({(t['loader'], t['minecraft']) for t in targets}, expected)
        for target in targets:
            project = root / target['project']
            self.assertTrue((project / 'build.gradle').is_file())
            self.assertTrue((project / 'gradlew.bat').is_file())
            self.assertTrue(target['buildTasks'])
            self.assertIn('{release}', target['artifact'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
